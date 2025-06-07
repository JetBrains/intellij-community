// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class CompositeElement extends TreeElement {
  private static final Logger LOG = Logger.getInstance(CompositeElement.class);
  private static final Key<Integer> OUR_HC_KEY = Key.create("OUR_HC_KEY");

  public static final CompositeElement[] EMPTY_ARRAY = new CompositeElement[0];

  private TreeElement firstChild;
  private TreeElement lastChild;

  private volatile int myCachedLength = -1;

  private volatile PsiElement myWrapper;
  private static final AtomicReferenceFieldUpdater<CompositeElement, PsiElement>
    myWrapperUpdater = AtomicReferenceFieldUpdater.newUpdater(CompositeElement.class, PsiElement.class, "myWrapper");


  public CompositeElement(@NotNull IElementType type) {
    super(type);
  }

  @Override
  public @NotNull CompositeElement clone() {
    CompositeElement clone = (CompositeElement)super.clone();

    clone.firstChild = null;
    clone.lastChild = null;
    clone.myWrapper = null;
    for (ASTNode child = rawFirstChild(); child != null; child = child.getTreeNext()) {
      clone.rawAddChildrenWithoutNotifications((TreeElement)child.clone());
    }
    clone.clearCaches();
    return clone;
  }

  public void subtreeChanged() {
    CompositeElement compositeElement = this;
    while(compositeElement != null) {
      compositeElement.clearCaches();
      if (!(compositeElement instanceof PsiElement)) {
        PsiElement psi = compositeElement.myWrapper;
        if (psi instanceof ASTDelegatePsiElement) {
          ((ASTDelegatePsiElement)psi).subtreeChanged();
        }
        else if (psi instanceof PsiFile) {
          ((PsiFile)psi).subtreeChanged();
          assertThreading((PsiFile)psi);
        }
      }

      compositeElement = compositeElement.getTreeParent();
    }
  }

  @Override
  public void clearCaches() {
    myCachedLength = -1;

    this.putUserData(OUR_HC_KEY, null);

    clearRelativeOffsets(rawFirstChild());
  }

  private static void assertThreading(@NotNull PsiFile file) {
    if (!ApplicationManager.getApplication().isWriteAccessAllowed() && !isNonPhysicalOrInjected(file)) {
      LOG.error("Threading assertion. " + getThreadingDiagnostics(file));
    }
  }

  private static @NonNls String getThreadingDiagnostics(@NotNull PsiFile psiFile) {
    return "psiFile: " + psiFile +
           "; psiFile.getViewProvider(): " + psiFile.getViewProvider() +
           "; psiFile.isPhysical(): " + psiFile.isPhysical() +
           "; nonPhysicalOrInjected: " + isNonPhysicalOrInjected(psiFile);
  }

  private static boolean isNonPhysicalOrInjected(@NotNull PsiFile psiFile) {
    return psiFile instanceof DummyHolder || psiFile.getViewProvider() instanceof FreeThreadedFileViewProvider || !psiFile.isPhysical();
  }

  @Override
  public void acceptTree(@NotNull TreeElementVisitor visitor) {
    visitor.visitComposite(this);
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    TreeElement element = this;
    if (element.getTreeParent() == null && offset >= element.getTextLength()) {
      return null;
    }
    startFind:
    while (true) {
      TreeElement child = element.getFirstChildNode();
      TreeElement lastChild = element.getLastChildNode();
      int elementTextLength = element.getTextLength();
      boolean fwd = lastChild == null || elementTextLength / 2 > offset;
      if (!fwd) {
        child = lastChild;
        offset = elementTextLength - offset;
      }
      while (child != null) {
        int textLength = child.getTextLength();
        if (textLength > offset || !fwd && textLength >= offset) {
          if (child instanceof LeafElement) {
            if (child instanceof ForeignLeafPsiElement) {
              child = fwd ? child.getTreeNext() : child.getTreePrev();
              continue;
            }
            return (LeafElement)child;
          }
          offset = fwd ? offset : textLength - offset;
          element = child;
          continue startFind;
        }
        offset -= textLength;
        child = fwd ? child.getTreeNext() : child.getTreePrev();
      }
      return null;
    }
  }

  public @Nullable PsiElement findPsiChildByType(@NotNull IElementType type) {
    ASTNode node = findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  public @Nullable PsiElement findPsiChildByType(@NotNull TokenSet types) {
    ASTNode node = findChildByType(types);
    return node == null ? null : node.getPsi();
  }

  @Override
  public ASTNode findChildByType(@NotNull IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      assertReadAccessAllowed();
    }

    for(ASTNode element = getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (element.getElementType() == type) {
        return element;
      }
    }
    return null;
  }

  @Override
  public ASTNode findChildByType(@NotNull IElementType type, ASTNode anchor) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      assertReadAccessAllowed();
    }

    return TreeUtil.findSibling(anchor, type);
  }

  @Override
  public @Nullable ASTNode findChildByType(@NotNull TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      assertReadAccessAllowed();
    }
    for(ASTNode element = getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (types.contains(element.getElementType())) {
        return element;
      }
    }
    return null;
  }

  @Override
  public @Nullable ASTNode findChildByType(@NotNull TokenSet typesSet, ASTNode anchor) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      assertReadAccessAllowed();
    }
    return TreeUtil.findSibling(anchor, typesSet);
  }

  /**
   * @implNote Optimization. Instead of just calling new {@code String(textToCharArray())} we try to delegate the text computation to the
   * first child, if there's only one, in hope it has optimized its own {@code getText()} and we thus can skip allocating buffer in
   * {@link AstBufferUtil}
   */
  @Override
  public @NotNull String getText() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) {
      return "";
    }
    else if (firstChildNode == getLastChildNode()) {
      if (firstChildNode instanceof ForeignLeafPsiElement) {
        return "";
      }
      return firstChildNode.getText();
    }
    return new String(textToCharArray());
  }

  @Override
  public @NotNull CharSequence getChars() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) {
      return "";
    }
    else if (firstChildNode == getLastChildNode()) {
      return firstChildNode.getChars();
    }
    return getText();
  }

  @Override
  public char @NotNull [] textToCharArray() {
    assertReadAccessAllowed();

    int len = getTextLength();
    char[] buffer = new char[len];
    int endOffset;
    try {
      endOffset = AstBufferUtil.toBuffer(this, buffer, 0);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      @NonNls String msg = "Underestimated text length: " + len;
      try {
        int length = AstBufferUtil.toBuffer(this, new char[len], 0);
        msg += ";\n repetition gives success (" + length + ")";
      }
      catch (ArrayIndexOutOfBoundsException e1) {
        msg += ";\n repetition fails as well";
      }
      throw new RuntimeException(msg, e);
    }
    if (endOffset != len) {
      @NonNls String msg = "len=" + len + ";\n endOffset=" + endOffset;
      msg += diagnoseTextInconsistency(new String(buffer, 0, Math.min(len, endOffset)));
      throw new AssertionError(msg);
    }
    return buffer;
  }

  private String diagnoseTextInconsistency(String text) {
    @NonNls String msg = "";
    msg += ";\n nonPhysicalOrInjected=" + isNonPhysicalOrInjected(SharedImplUtil.getContainingFile(this));
    msg += ";\n buffer=" + text;
    try {
      msg += ";\n this=" + this;
    }
    catch (StackOverflowError e) {
      msg += ";\n this.toString produces SOE";
    }
    int shitStart = textMatches(text, 0);
    msg += ";\n matches until " + shitStart;
    LeafElement leaf = findLeafElementAt(Math.abs(shitStart));
    msg += ";\n element there=" + leaf;
    if (leaf != null) {
      PsiElement psi = leaf.getPsi();
      msg += ";\n leaf.text=" + leaf.getText();
      msg += ";\n leaf.psi=" + psi;
      msg += ";\n leaf.lang=" + (psi == null ? null : psi.getLanguage());
      msg += ";\n leaf.type=" + leaf.getElementType();
    }
    PsiElement psi = getPsi();
    if (psi != null) {
      boolean valid = psi.isValid();
      msg += ";\n psi.valid=" + valid;
      if (valid) {
        PsiFile file = psi.getContainingFile();
        if (file != null) {
          msg += ";\n psi.file=" + file;
          msg += ";\n psi.file.tl=" + file.getTextLength();
          msg += ";\n psi.file.lang=" + file.getLanguage();
          msg += ";\n psi.file.vp=" + file.getViewProvider();
          msg += ";\n psi.file.vp.lang=" + file.getViewProvider().getLanguages();
          msg += ";\n psi.file.vp.lang=" + file.getViewProvider().getLanguages();

          PsiElement fileLeaf = file.findElementAt(getTextRange().getStartOffset());
          LeafElement myLeaf = findLeafElementAt(0);
          msg += ";\n leaves at start=" + fileLeaf + " and " + myLeaf;
        }
      }
    }
    return msg;
  }

  @Override
  public boolean textContains(char c) {
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.textContains(c)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    int[] curOffset = {start};
    acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        matchText(leaf);
      }

      private void matchText(TreeElement leaf) {
        curOffset[0] = leaf.textMatches(buffer, curOffset[0]);
        if (curOffset[0] < 0) {
          stopWalking();
        }
      }

      @Override
      public void visitComposite(CompositeElement composite) {
        if (composite instanceof LazyParseableElement && !((LazyParseableElement)composite).isParsed()) {
          matchText(composite);
        }
        else {
          super.visitComposite(composite);
        }
      }
    });
    return curOffset[0];
  }

  public final @Nullable PsiElement findChildByRoleAsPsiElement(int role) {
    ASTNode element = findChildByRole(role);
    if (element == null) {
      return null;
    }
    return SourceTreeToPsiMap.treeElementToPsi(element);
  }

  public @Nullable ASTNode findChildByRole(int role) {
    // assert ChildRole.isUnique(role);
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (getChildRole(child) == role) {
        return child;
      }
    }
    return null;
  }

  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this, child);
    return 0; //ChildRole.NONE;
  }

  protected final int getChildRole(@NotNull ASTNode child, int roleCandidate) {
    if (findChildByRole(roleCandidate) == child) {
      return roleCandidate;
    }
    return 0; //ChildRole.NONE;
  }

  @Override
  public ASTNode @NotNull [] getChildren(@Nullable TokenSet filter) {
    int count = countChildren(filter);
    if (count == 0) {
      return EMPTY_ARRAY;
    }
    ASTNode[] result = new ASTNode[count];
    count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
        result[count++] = child;
      }
    }
    return result;
  }

  public <T extends PsiElement> T @NotNull [] getChildrenAsPsiElements(@Nullable TokenSet filter, @NotNull ArrayFactory<? extends T> constructor) {
    assertReadAccessAllowed();
    int count = countChildren(filter);
    T[] result = constructor.create(count);
    if (count == 0) {
      return result;
    }
    int idx = 0;
    for (ASTNode child = getFirstChildNode(); child != null && idx < count; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
        @SuppressWarnings("unchecked") T element = (T)child.getPsi();
        LOG.assertTrue(element != null, child);
        result[idx++] = element;
      }
    }
    return result;
  }

  public <T extends PsiElement> T @NotNull [] getChildrenAsPsiElements(@NotNull IElementType type, @NotNull ArrayFactory<? extends T> constructor) {
    assertReadAccessAllowed();
    int count = countChildren(type);
    T[] result = constructor.create(count);
    if (count == 0) {
      return result;
    }
    int idx = 0;
    for (ASTNode child = getFirstChildNode(); child != null && idx < count; child = child.getTreeNext()) {
      if (type == child.getElementType()) {
        @SuppressWarnings("unchecked") T element = (T)child.getPsi();
        LOG.assertTrue(element != null, child);
        result[idx++] = element;
      }
    }
    return result;
  }

  public int countChildren(@Nullable TokenSet filter) {
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
        count++;
      }
    }

    return count;
  }

  private int countChildren(@NotNull IElementType type) {
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (type == child.getElementType()) {
        count++;
      }
    }

    return count;
  }

  /**
   * @return First element that was appended (for example whitespaces could be skipped)
   */
  public TreeElement addInternal(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
    ASTNode anchorBefore;
    if (anchor == null) {
      anchorBefore = before == null || before.booleanValue() ? null : getFirstChildNode();
    }
    else {
      anchorBefore = before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    return (TreeElement)CodeEditUtil.addChildren(this, first, last, anchorBefore);
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    CodeEditUtil.removeChild(this, child);
  }

  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    CodeEditUtil.replaceChild(this, child, newElement);
  }

  @Override
  public int getTextLength() {
    int cachedLength = myCachedLength;
    if (cachedLength >= 0) {
      return cachedLength;
    }

    assertReadAccessAllowed(); //otherwise a write action can modify the tree while we're walking it
    try {
      return walkCachingLength();
    }
    catch (AssertionError e) {
      myCachedLength = -1;
      String assertion = StringUtil.getThrowableText(e);
      throw new AssertionError("Walking failure: ===\n"+assertion+"\n=== Thread dump:\n"+ ThreadDumper.dumpThreadsToString()+"\n===\n");
    }
  }

  @Override
  public int hc() {
    Integer cached = getUserData(OUR_HC_KEY);
    if (cached != null) {
      return cached;
    }

    int hc = 0;
    TreeElement child = firstChild;
    while (child != null) {
      hc += child.hc();
      child = child.getTreeNext();
    }
    putUserDataIfAbsent(OUR_HC_KEY, hc);

    return hc;
  }

  @Override
  public int getCachedLength() {
    return myCachedLength;
  }

  private static @NotNull TreeElement drillDown(@NotNull TreeElement start) {
    TreeElement cur = start;
    while (cur.getCachedLength() < 0) {
      TreeElement child = cur.getFirstChildNode();
      if (child == null) {
        break;
      }
      cur = child;
    }
    return cur;
  }

  // returns computed length
  private int walkCachingLength() {
    TreeElement cur = drillDown(this);
    while (true) {
      int length = cur.getCachedLength();
      if (length < 0) {
        // can happen only in CompositeElement
        length = 0;
        for (TreeElement child = cur.getFirstChildNode(); child != null; child = child.getTreeNext()) {
          length += child.getTextLength();
        }
        ((CompositeElement)cur).setCachedLength(length);
      }

      if (cur == this) {
        return length;
      }

      TreeElement next = cur.getTreeNext();
      cur = next != null ? drillDown(next) : getNotNullParent(cur);
    }
  }

  private static TreeElement getNotNullParent(TreeElement cur) {
    TreeElement parent = cur.getTreeParent();
    if (parent == null) {
      diagnoseNullParent(cur);
    }
    return parent;
  }

  private static void diagnoseNullParent(TreeElement cur) {
    PsiElement psi = cur.getPsi();
    if (psi != null) {
      PsiUtilCore.ensureValid(psi);
    }
    throw new IllegalStateException("Null parent of " + cur + " " + cur.getClass());
  }

  void setCachedLength(int cachedLength) {
    myCachedLength = cachedLength;
  }

  @Override
  public TreeElement getFirstChildNode() {
    return firstChild;
  }

  @Override
  public TreeElement getLastChildNode() {
    return lastChild;
  }

  void setFirstChildNode(TreeElement firstChild) {
    this.firstChild = firstChild;
    clearRelativeOffsets(firstChild);
  }

  void setLastChildNode(TreeElement lastChild) {
    this.lastChild = lastChild;
  }

  @Override
  public void addChild(@NotNull ASTNode child, @Nullable ASTNode anchorBefore) {
    LOG.assertTrue(anchorBefore == null || ((TreeElement)anchorBefore).getTreeParent() == this, "anchorBefore == null || anchorBefore.getTreeParent() == parent");
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(child);
    TreeElement last = ((TreeElement)child).getTreeNext();
    TreeElement first = (TreeElement)child;

    removeChildrenInner(first, last);

    ChangeUtil.prepareAndRunChangeAction(destinationTreeChange -> {
      if (anchorBefore != null) {
        insertBefore((TreeChangeEventImpl)destinationTreeChange, (TreeElement)anchorBefore, first);
      }
      else {
        add((TreeChangeEventImpl)destinationTreeChange, this, first);
      }
    }, this);
  }

  @Override
  public void addLeaf(@NotNull IElementType leafType, @NotNull CharSequence leafText, ASTNode anchorBefore) {
    FileElement holder = new DummyHolder(getManager(), null).getTreeElement();
    LeafElement leaf = ASTFactory.leaf(leafType, holder.getCharTable().intern(leafText));
    CodeEditUtil.setNodeGenerated(leaf, true);
    holder.rawAddChildren(leaf);

    addChild(leaf, anchorBefore);
  }

  @Override
  public void addChild(@NotNull ASTNode child) {
    addChild(child, null);
  }

  @Override
  public void removeChild(@NotNull ASTNode child) {
    removeChildInner((TreeElement)child);
  }

  @Override
  public void removeRange(@NotNull ASTNode first, @Nullable ASTNode firstWhichStayInTree) {
    removeChildrenInner((TreeElement)first, (TreeElement)firstWhichStayInTree);
  }

  @Override
  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    LOG.assertTrue(((TreeElement)oldChild).getTreeParent() == this);
    TreeElement oldChild1 = (TreeElement)oldChild;
    TreeElement newChildNext = ((TreeElement)newChild).getTreeNext();
    TreeElement newChild1 = (TreeElement)newChild;

    if(oldChild1 == newChild1) {
      return;
    }

    removeChildrenInner(newChild1, newChildNext);

    ChangeUtil.prepareAndRunChangeAction(destinationTreeChange -> {
      replace((TreeChangeEventImpl)destinationTreeChange, oldChild1, newChild1);
      repairRemovedElement(this, oldChild1);
    }, this);
  }

  @Override
  public void replaceAllChildrenToChildrenOf(@NotNull ASTNode anotherParent) {
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(anotherParent.getFirstChildNode());
    ASTNode firstChild = anotherParent.getFirstChildNode();
    ChangeUtil.prepareAndRunChangeAction(
      event -> remove((TreeChangeEventImpl)event, (TreeElement)anotherParent.getFirstChildNode(), null),
      (TreeElement)anotherParent);

    if (firstChild != null) {
      ChangeUtil.prepareAndRunChangeAction(destinationTreeChange -> {
        TreeElement first = getFirstChildNode();
        TreeChangeEventImpl event = (TreeChangeEventImpl)destinationTreeChange;
        CompositeElement parent = getTreeParent();
        if (parent != null) {
          // treat all replacements as one big childrenChanged to simplify resulting PSI/document events
          event.addElementaryChange(parent);
        }
        remove(event, first, null);
        add(event, this, (TreeElement)firstChild);
        if(parent != null) {
          repairRemovedElement(this, first);
        }
      }, this);
    }
    else {
      removeAllChildren();
    }
  }

  public void removeAllChildren() {
    TreeElement child = getFirstChildNode();
    if (child != null) {
      removeRange(child, null);
    }
  }

  @Override
  public void addChildren(@NotNull ASTNode firstChild, @Nullable ASTNode lastChild, @Nullable ASTNode anchorBefore) {
    ASTNode next;
    for (ASTNode f = firstChild; f != lastChild; f = next) {
      next = f.getTreeNext();
      addChild(f, anchorBefore);
    }
  }

  /**
   * Don't call this method, it's here for implementation reasons.
   */
  final @Nullable PsiElement getCachedPsi() {
    return myWrapper;
  }

  @Override
  public final PsiElement getPsi() {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    PsiElement wrapper = myWrapper;
    if (wrapper != null) {
      return wrapper;
    }

    wrapper = createPsiNoLock();
    return myWrapperUpdater.compareAndSet(this, null, wrapper) ? wrapper : Objects.requireNonNull(myWrapper);
  }

  @Override
  public <T extends PsiElement> T getPsi(@NotNull Class<T> clazz) {
    return LeafElement.getPsi(clazz, getPsi(), LOG);
  }

  protected PsiElement createPsiNoLock() {
    Language lang = getElementType().getLanguage();
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (parserDefinition != null) {
      return parserDefinition.createElement(this);
    }

    return null;
  }

  public void setPsi(@NotNull PsiElement psi) {
    PsiElement prev = myWrapper;
    if (prev != null && prev != psi) {
      DebugUtil.onInvalidated(prev);
    }
    myWrapper = psi;
  }

  void clearPsi() {
    myWrapper = null;
  }

  @Override
  public final void applyInsertOnReparse(@NotNull ASTNode newChild, ASTNode anchor) {
    TreeElement newTreeElement = (TreeElement) newChild;
    newTreeElement.rawRemove();
    if (anchor != null) {
      TreeElement anchorTreeElement = (TreeElement) anchor;
      anchorTreeElement.rawInsertAfterMe(newTreeElement);
    }
    else {
      TreeElement firstChildNode = getFirstChildNode();
      if (firstChildNode != null) {
        firstChildNode.rawInsertBeforeMe(newTreeElement);
      }
      else {
        rawAddChildren(newTreeElement);
      }
    }

    newTreeElement.clearCaches();
    subtreeChanged();
  }

  @Override
  public final void applyDeleteOnReparse(@NotNull ASTNode oldChild) {
    ((TreeElement) oldChild).rawRemove();
    subtreeChanged();
  }

  @Override
  public final void applyReplaceFileOnReparse(@NotNull PsiFile psiFile, @NotNull FileASTNode newNode) {
    if (getFirstChildNode() != null) rawRemoveAllChildren();
    ASTNode firstChildNode = newNode.getFirstChildNode();
    if (firstChildNode != null) rawAddChildren((TreeElement)firstChildNode);
    ((PsiFileImpl) psiFile).calcTreeElement().setCharTable(newNode.getCharTable());
    subtreeChanged();
  }

  public final void rawAddChildren(@NotNull TreeElement first) {
    rawAddChildrenWithoutNotifications(first);

    subtreeChanged();
  }

  public void rawAddChildrenWithoutNotifications(@NotNull TreeElement first) {
    TreeElement last = getLastChildNode();
    if (last == null){
      TreeElement chainLast = rawSetParents(first, this);
      setFirstChildNode(first);
      setLastChildNode(chainLast);
    }
    else {
      last.rawInsertAfterMeWithoutNotifications(first);
    }

    DebugUtil.checkTreeStructure(this);
  }

  static @NotNull TreeElement rawSetParents(@NotNull TreeElement child, @NotNull CompositeElement parent) {
    child.rawRemoveUpToWithoutNotifications(null, false);
    while (true) {
      child.setTreeParent(parent);
      TreeElement treeNext = child.getTreeNext();
      if (treeNext == null) {
        return child;
      }
      child = treeNext;
    }
  }

  public void rawRemoveAllChildren() {
    TreeElement first = getFirstChildNode();
    if (first != null) {
      first.rawRemoveUpToLast();
    }
  }

  private static void repairRemovedElement(@NotNull CompositeElement oldParent, TreeElement oldChild) {
    if (oldChild == null) {
      return;
    }
    FileElement treeElement = DummyHolderFactory.createHolder(oldParent.getManager(), null, false).getTreeElement();
    treeElement.rawAddChildren(oldChild);
  }

  private static void add(@NotNull TreeChangeEventImpl destinationTreeChange, @NotNull CompositeElement parent, @NotNull TreeElement first) {
    destinationTreeChange.addElementaryChange(parent);
    parent.rawAddChildren(first);
  }

  private static void remove(@NotNull TreeChangeEventImpl destinationTreeChange, @Nullable TreeElement first, @Nullable TreeElement last) {
    if (first != null) {
      destinationTreeChange.addElementaryChange(first.getTreeParent());
      first.rawRemoveUpTo(last);
    }
  }

  private static void insertBefore(@NotNull TreeChangeEventImpl destinationTreeChange, @NotNull TreeElement anchorBefore, @NotNull TreeElement first) {
    destinationTreeChange.addElementaryChange(anchorBefore.getTreeParent());
    anchorBefore.rawInsertBeforeMe(first);
  }

  private static void replace(@NotNull TreeChangeEventImpl sourceTreeChange, @NotNull TreeElement oldChild, @NotNull TreeElement newChild) {
    sourceTreeChange.addElementaryChange(oldChild.getTreeParent());
    oldChild.rawReplaceWithList(newChild);
  }

  private static void removeChildInner(@NotNull TreeElement child) {
    removeChildrenInner(child, child.getTreeNext());
  }

  private static void removeChildrenInner(@NotNull TreeElement first, @Nullable TreeElement last) {
    FileElement fileElement = TreeUtil.getFileElement(first);
    if (fileElement != null) {
      ChangeUtil.prepareAndRunChangeAction(destinationTreeChange -> {
        remove((TreeChangeEventImpl)destinationTreeChange, first, last);
        repairRemovedElement(fileElement, first);
      }, first.getTreeParent());
    }
    else {
      first.rawRemoveUpTo(last);
    }
  }


  public TreeElement rawFirstChild() {
    return firstChild;
  }

  public TreeElement rawLastChild() {
    return lastChild;
  }
}
