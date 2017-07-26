/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.tree;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeElement extends TreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.CompositeElement");
  public static final CompositeElement[] EMPTY_ARRAY = new CompositeElement[0];

  private TreeElement firstChild;
  private TreeElement lastChild;

  private volatile int myCachedLength = -1;
  private volatile int myHC = -1;
  private volatile PsiElement myWrapper;
  private static final boolean ASSERT_THREADING = true;//DebugUtil.CHECK || ApplicationManagerEx.getApplicationEx().isInternal() || ApplicationManagerEx.getApplicationEx().isUnitTestMode();

  private static final AtomicFieldUpdater<CompositeElement, PsiElement> ourPsiUpdater =
    AtomicFieldUpdater.forFieldOfType(CompositeElement.class, PsiElement.class);

  public CompositeElement(@NotNull IElementType type) {
    super(type);
  }

  @NotNull
  @Override
  public CompositeElement clone() {
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
        final PsiElement psi = compositeElement.myWrapper;
        if (psi instanceof ASTDelegatePsiElement) {
          ((ASTDelegatePsiElement)psi).subtreeChanged();
        }
        else if (psi instanceof PsiFile) {
          ((PsiFile)psi).subtreeChanged();
        }
      }

      compositeElement = compositeElement.getTreeParent();
    }
  }

  @Override
  public void clearCaches() {
    assertThreading();
    myCachedLength = -1;

    myHC = -1;

    clearRelativeOffsets(rawFirstChild());
  }

  private void assertThreading() {
    if (ASSERT_THREADING) {
      boolean ok = ApplicationManager.getApplication().isWriteAccessAllowed() || isNonPhysicalOrInjected();
      if (!ok) {
        LOG.error("Threading assertion. " + getThreadingDiagnostics());
      }
    }
  }

  private String getThreadingDiagnostics() {
    FileElement fileElement;PsiFile psiFile;
    return " Under write: " + ApplicationManager.getApplication().isWriteAccessAllowed() +
           "; wrapper: " + myWrapper +
           "; wrapper.isPhysical(): " + (myWrapper != null && myWrapper.isPhysical()) +
           "; fileElement: " + (fileElement = TreeUtil.getFileElement(this)) +
           "; psiFile: " + (psiFile = fileElement == null ? null : (PsiFile)fileElement.getPsi()) +
           "; psiFile.getViewProvider(): " + (psiFile == null ? null : psiFile.getViewProvider()) +
           "; psiFile.isPhysical(): " + (psiFile != null && psiFile.isPhysical()) +
           "; nonPhysicalOrInjected: " + isNonPhysicalOrInjected();
  }

  private boolean isNonPhysicalOrInjected() {
    FileElement fileElement = TreeUtil.getFileElement(this);
    if (fileElement == null || fileElement instanceof DummyHolderElement) return true;
    if (fileElement.getTreeParent() != null) return true; // dummy holder
    PsiElement wrapper = this instanceof PsiElement ? (PsiElement)this : myWrapper;
    if (wrapper == null) return true;
    PsiFile psiFile = wrapper.getContainingFile();
    return
      psiFile ==  null ||
      psiFile instanceof DummyHolder ||
      psiFile.getViewProvider() instanceof FreeThreadedFileViewProvider ||
      !psiFile.isPhysical();
  }

  @Override
  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitComposite(this);
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    TreeElement element = this;
    if (element.getTreeParent() == null && offset >= element.getTextLength()) return null;
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
        final int textLength = child.getTextLength();
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

  @Nullable
  public PsiElement findPsiChildByType(IElementType type) {
    final ASTNode node = findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  @Nullable
  public PsiElement findPsiChildByType(TokenSet types) {
    final ASTNode node = findChildByType(types);
    return node == null ? null : node.getPsi();
  }

  @Override
  public ASTNode findChildByType(IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    for(ASTNode element = getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  @Override
  public ASTNode findChildByType(IElementType type, ASTNode anchor) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    return TreeUtil.findSibling(anchor, type);
  }

  @Override
  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (types.contains(element.getElementType())) return element;
    }
    return null;
  }

  @Override
  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet, ASTNode anchor) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    return TreeUtil.findSibling(anchor, typesSet);
  }

  @Override
  @NotNull
  public String getText() {
    return StringFactory.createShared(textToCharArray());
  }

  @NotNull
  @Override
  public CharSequence getChars() {
    return getText();
    //return new CharArrayCharSequence(textToCharArray());
  }

  @Override
  public int getNotCachedLength() {
    final int[] result = {0};

    acceptTree(new RecursiveTreeElementWalkingVisitor(false) {
      @Override
      protected void visitNode(final TreeElement element) {
        if (element instanceof LeafElement || TreeUtil.isCollapsedChameleon(element)) {
          result[0] += element.getNotCachedLength();
        }
        super.visitNode(element);
      }
    });

    return result[0];
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final int len = getTextLength();
    char[] buffer = new char[len];
    final int endOffset;
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
    msg += ";\n nonPhysicalOrInjected=" + isNonPhysicalOrInjected();
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
      if (child.textContains(c)) return true;
    }
    return false;
  }

  protected int textMatches(@NotNull final CharSequence buffer, final int start) {
    final int[] curOffset = {start};
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

  @Nullable
  public final PsiElement findChildByRoleAsPsiElement(int role) {
    ASTNode element = findChildByRole(role);
    if (element == null) return null;
    return SourceTreeToPsiMap.treeElementToPsi(element);
  }

  @Nullable
  public ASTNode findChildByRole(int role) {
    // assert ChildRole.isUnique(role);
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (getChildRole(child) == role) return child;
    }
    return null;
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this, child);
    return 0; //ChildRole.NONE;
  }

  protected final int getChildRole(ASTNode child, int roleCandidate) {
    if (findChildByRole(roleCandidate) == child) {
      return roleCandidate;
    }
    return 0; //ChildRole.NONE;
  }

  @NotNull
  @Override
  public ASTNode[] getChildren(@Nullable TokenSet filter) {
    int count = countChildren(filter);
    if (count == 0) {
      return EMPTY_ARRAY;
    }
    final ASTNode[] result = new ASTNode[count];
    count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
        result[count++] = child;
      }
    }
    return result;
  }

  @NotNull
  public <T extends PsiElement> T[] getChildrenAsPsiElements(@Nullable TokenSet filter, ArrayFactory<T> constructor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
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

  @NotNull
  public <T extends PsiElement> T[] getChildrenAsPsiElements(@NotNull IElementType type, ArrayFactory<T> constructor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
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
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ASTNode anchorBefore;
    if (anchor != null) {
      anchorBefore = before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    else {
      anchorBefore = before == null || before.booleanValue() ? null : getFirstChildNode();
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
    if (cachedLength >= 0) return cachedLength;

    ApplicationManager.getApplication().assertReadAccessAllowed(); //otherwise a write action can modify the tree while we're walking it
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
    int hc = myHC;
    if (hc == -1) {
      hc = 0;
      TreeElement child = firstChild;
      while (child != null) {
        hc += child.hc();
        child = child.getTreeNext();
      }
      myHC = hc;
    }
    return hc;
  }

  @Override
  public int getCachedLength() {
    return myCachedLength;
  }

  @NotNull
  private static TreeElement drillDown(@NotNull TreeElement start) {
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
  public void addChild(@NotNull ASTNode child, @Nullable final ASTNode anchorBefore) {
    LOG.assertTrue(anchorBefore == null || ((TreeElement)anchorBefore).getTreeParent() == this, "anchorBefore == null || anchorBefore.getTreeParent() == parent");
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(child);
    final TreeElement last = ((TreeElement)child).getTreeNext();
    final TreeElement first = (TreeElement)child;

    removeChildrenInner(first, last);

    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
      @Override
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        if (anchorBefore != null) {
          insertBefore(destinationTreeChange, (TreeElement)anchorBefore, first);
        }
        else {
          add(destinationTreeChange, CompositeElement.this, first);
        }
      }
    }, this);
  }

  @Override
  public void addLeaf(@NotNull final IElementType leafType, final CharSequence leafText, final ASTNode anchorBefore) {
    FileElement holder = new DummyHolder(getManager(), null).getTreeElement();
    final LeafElement leaf = ASTFactory.leaf(leafType, holder.getCharTable().intern(leafText));
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
  public void removeRange(@NotNull ASTNode first, ASTNode firstWhichStayInTree) {
    removeChildrenInner((TreeElement)first, (TreeElement)firstWhichStayInTree);
  }

  @Override
  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    LOG.assertTrue(((TreeElement)oldChild).getTreeParent() == this);
    final TreeElement oldChild1 = (TreeElement)oldChild;
    final TreeElement newChildNext = ((TreeElement)newChild).getTreeNext();
    final TreeElement newChild1 = (TreeElement)newChild;

    if(oldChild1 == newChild1) return;

    removeChildrenInner(newChild1, newChildNext);

    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
      @Override
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        replace(destinationTreeChange, oldChild1, newChild1);
        repairRemovedElement(CompositeElement.this, oldChild1);
      }
    }, this);
  }

  @Override
  public void replaceAllChildrenToChildrenOf(final ASTNode anotherParent) {
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(anotherParent.getFirstChildNode());
    final ASTNode firstChild = anotherParent.getFirstChildNode();
    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
      @Override
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        destinationTreeChange.addElementaryChange(anotherParent, ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, anotherParent));
        ((CompositeElement)anotherParent).rawRemoveAllChildren();
      }
    }, (TreeElement)anotherParent);

    if (firstChild != null) {
      ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
        @Override
        public void makeChange(TreeChangeEvent destinationTreeChange) {
          if(getTreeParent() != null){
            final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, CompositeElement.this);
            changeInfo.setOldLength(getTextLength());
            destinationTreeChange.addElementaryChange(CompositeElement.this, changeInfo);
            rawRemoveAllChildren();
            rawAddChildren((TreeElement)firstChild);
          }
          else{
            final TreeElement first = getFirstChildNode();
            remove(destinationTreeChange, first, null);
            add(destinationTreeChange, CompositeElement.this, (TreeElement)firstChild);
            repairRemovedElement(CompositeElement.this, first);
          }
        }
      }, this);
    }
    else {
      removeAllChildren();
    }
  }

  public void removeAllChildren() {
    final TreeElement child = getFirstChildNode();
    if (child != null) {
      removeRange(child, null);
    }
  }

  @Override
  public void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore) {
    while (firstChild != lastChild) {
      final ASTNode next1 = firstChild.getTreeNext();
      addChild(firstChild, anchorBefore);
      firstChild = next1;
    }
  }

  /**
   * Don't call this method, it's public for implementation reasons.
   */
  @Nullable
  public final PsiElement getCachedPsi() {
    return myWrapper;
  }

  @Override
  public final PsiElement getPsi() {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    PsiElement wrapper = myWrapper;
    if (wrapper != null) return wrapper;

    wrapper = obtainStubBasedPsi();
    if (wrapper == null) wrapper = createPsiNoLock();
    return ourPsiUpdater.compareAndSet(this, null, wrapper) ? wrapper : ObjectUtils.assertNotNull(myWrapper);
  }

  /**
   * If AST has been gced and recreated, but someone still holds a reference to a PSI, then {@link #getPsi()} should return the very same PSI object.
   * So we try to find that PSI in file's {@link AstPathPsiMap}.
   */
  @Nullable
  private PsiElement obtainStubBasedPsi() {
    AstPath path = getElementType() instanceof IStubElementType ? AstPath.getNodePath(this) : null;
    return path == null ? null : path.getContainingFile().obtainPsi(path, () -> (StubBasedPsiElementBase<?>)createPsiNoLock());
  }

  @Override
  public <T extends PsiElement> T getPsi(@NotNull Class<T> clazz) {
    return LeafElement.getPsi(clazz, getPsi(), LOG);
  }

  protected PsiElement createPsiNoLock() {
    final Language lang = getElementType().getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (parserDefinition != null) {
      return parserDefinition.createElement(this);
    }

    //noinspection ConstantConditions
    return null;
  }

  public void setPsi(@NotNull PsiElement psi) {
    myWrapper = psi;
  }

  protected void clearPsi() {
    myWrapper = null;
  }

  public final void rawAddChildren(@NotNull TreeElement first) {
    rawAddChildrenWithoutNotifications(first);

    subtreeChanged();
  }

  public void rawAddChildrenWithoutNotifications(@NotNull TreeElement first) {
    if (DebugUtil.DO_EXPENSIVE_CHECKS && !(this instanceof LazyParseableElement)) {
      PsiFileImpl file = getCachedFile(this);
      if (file != null && !file.useStrongRefs()) {
        throw new AssertionError("Attempt to modify PSI in a file with weakly-referenced AST. Possible cause: missing PomTransaction.");
      }
    }

    final TreeElement last = getLastChildNode();
    if (last == null){
      first.rawRemoveUpToWithoutNotifications(null, false);
      setFirstChildNode(first);
      while(true){
        final TreeElement treeNext = first.getTreeNext();
        first.setTreeParent(this);
        if(treeNext == null) break;
        first = treeNext;
      }
      setLastChildNode(first);
      first.setTreeParent(this);
    }
    else {
      last.rawInsertAfterMeWithoutNotifications(first);
    }

    DebugUtil.checkTreeStructure(this);
  }

  public void rawRemoveAllChildren() {
    TreeElement first = getFirstChildNode();
    if (first != null) {
      first.rawRemoveUpToLast();
    }
  }

  private static void repairRemovedElement(final CompositeElement oldParent, final TreeElement oldChild) {
    if(oldChild == null) return;
    final FileElement treeElement = DummyHolderFactory.createHolder(oldParent.getManager(), null, false).getTreeElement();
    treeElement.rawAddChildren(oldChild);
  }

  private static void add(final TreeChangeEvent destinationTreeChange,
                          final CompositeElement parent,
                          final TreeElement first) {
    parent.rawAddChildren(first);
    TreeElement child = first;
    while(child != null){
      destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.ADD, child));
      child = child.getTreeNext();
    }
  }

  private static void remove(final TreeChangeEvent destinationTreeChange,
                             final TreeElement first,
                             final TreeElement last) {
    if (first != null) {
      TreeElement child = first;
      while(child != last && child != null){
        destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
        child = child.getTreeNext();
      }

      first.rawRemoveUpTo(last);
    }
  }

  private static void insertBefore(final TreeChangeEvent destinationTreeChange,
                                   final TreeElement anchorBefore,
                                   final TreeElement first) {
    anchorBefore.rawInsertBeforeMe(first);
    TreeElement child = first;
    while(child != anchorBefore){
      destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.ADD, child));
      child = child.getTreeNext();
    }
  }

  private static void replace(final TreeChangeEvent sourceTreeChange,
                              final TreeElement oldChild,
                              final TreeElement newChild) {
    oldChild.rawReplaceWithList(newChild);
    final ReplaceChangeInfoImpl change = new ReplaceChangeInfoImpl(newChild);
    sourceTreeChange.addElementaryChange(newChild, change);
    change.setReplaced(oldChild);
  }

  private static void removeChildInner(final TreeElement child) {
    removeChildrenInner(child, child.getTreeNext());
  }

  private static void removeChildrenInner(final TreeElement first, final TreeElement last) {
    final FileElement fileElement = TreeUtil.getFileElement(first);
    if (fileElement != null) {
      ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction() {
        @Override
        public void makeChange(TreeChangeEvent destinationTreeChange) {
          remove(destinationTreeChange, first, last);
          repairRemovedElement(fileElement, first);
        }
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
