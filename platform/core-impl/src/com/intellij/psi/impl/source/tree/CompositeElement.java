/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderElement;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompositeElement extends TreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.CompositeElement");

  private TreeElement firstChild = null;
  private TreeElement lastChild = null;

  private volatile int myModificationsCount = 0;
  private static final int NOT_CACHED = -239;
  private volatile int myCachedLength = NOT_CACHED;
  private volatile int myHC = -1;
  private volatile PsiElement myWrapper = null;
  private static final boolean ASSERT_THREADING = true;//DebugUtil.CHECK || ApplicationManagerEx.getApplicationEx().isInternal() || ApplicationManagerEx.getApplicationEx().isUnitTestMode();

  public CompositeElement(@NotNull IElementType type) {
    super(type);
  }

  public int getModificationCount() {
    return myModificationsCount;
  }

  @Override
  public CompositeElement clone() {
    CompositeElement clone = (CompositeElement)super.clone();

    synchronized (PsiLock.LOCK) {
      clone.firstChild = null;
      clone.lastChild = null;
      clone.myModificationsCount = 0;
      clone.myWrapper = null;
      for (ASTNode child = rawFirstChild(); child != null; child = child.getTreeNext()) {
        clone.rawAddChildrenWithoutNotifications((TreeElement)child.clone());
      }
      clone.clearCaches();
    }
    return clone;
  }

  public void subtreeChanged() {
    synchronized (PsiLock.LOCK) {
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
  }

  @Override
  public void clearCaches() {
    assertThreading();
    myCachedLength = NOT_CACHED;

    myModificationsCount++;
    myHC = -1;

    clearRelativeOffsets(rawFirstChild());
  }

  public void assertThreading() {
    if (ASSERT_THREADING) {
      boolean ok = ApplicationManager.getApplication().isWriteAccessAllowed() ||
                   //Thread.holdsLock(START_OFFSET_LOCK) ||
                   isNonPhysicalOrInjected();
      if (!ok) {
        FileElement fileElement;
        PsiFile psiFile;
        LOG.error("Threading assertion. " +
                  " Under write: " + ApplicationManager.getApplication().isWriteAccessAllowed() +
                  "; Thread.holdsLock(START_OFFSET_LOCK): " + Thread.holdsLock(START_OFFSET_LOCK) +
                  "; Thread.holdsLock(PsiLock.LOCK): " + Thread.holdsLock(PsiLock.LOCK) +
                  "; wrapper: " + myWrapper +
                  "; wrapper.isPhysical(): " + (myWrapper != null && myWrapper.isPhysical()) +
                  "; fileElement: " +(fileElement = TreeUtil.getFileElement(this))+
                  "; psiFile: " + (psiFile = fileElement == null ? null : (PsiFile)fileElement.getPsi()) +
                  "; psiFile.getViewProvider(): " + (psiFile == null ? null : psiFile.getViewProvider()) +
                  "; psiFile.isPhysical(): " + (psiFile != null && psiFile.isPhysical())
        );
      }
    }
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
    startFind:
    while (true) {
      TreeElement child = element.getFirstChildNode();
      while (child != null) {
        final int textLength = child.getTextLength();
        if (textLength > offset) {
          if (child instanceof LeafElement) {
            if (child instanceof ForeignLeafPsiElement) {
              child = child.getTreeNext();
              continue;
            }
            return (LeafElement)child;
          }
          element = child;
          continue startFind;
        }
        offset -= textLength;
        child = child.getTreeNext();
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

    ASTNode child = anchor;
    while (true) {
      if (child == null) return null;
      if (type == child.getElementType()) return child;
      child = child.getTreeNext();
    }
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
    ASTNode child = anchor;
    while (true) {
      if (child == null) return null;
      if (typesSet.contains(child.getElementType())) return child;
      child = child.getTreeNext();
    }
  }

  @Override
  @NotNull
  public String getText() {
    return StringFactory.createShared(textToCharArray());
  }

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
    int startStamp = myModificationsCount;

    final int len = getTextLength();

    if (startStamp != myModificationsCount) {
      throw new AssertionError("Tree changed while calculating text. startStamp:"+startStamp+"; current:"+myModificationsCount+"; myHC:"+myHC+"; assertThreading:"+ASSERT_THREADING+"; Thread.holdsLock(START_OFFSET_LOCK):"+Thread.holdsLock(START_OFFSET_LOCK)+"; Thread.holdsLock(PSI_LOCK):"+Thread.holdsLock(PsiLock.LOCK)+"; this: " + this);
    }

    char[] buffer = new char[len];
    final int endOffset;
    try {
      endOffset = AstBufferUtil.toBuffer(this, buffer, 0);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      @NonNls String msg = "Underestimated text length: " + len;
      msg += diagnoseTextInconsistency(new String(buffer), startStamp);
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
      msg += diagnoseTextInconsistency(new String(buffer, 0, Math.min(len, endOffset)), startStamp);
      throw new AssertionError(msg);
    }
    return buffer;
  }

  private String diagnoseTextInconsistency(String text, int startStamp) {
    @NonNls String msg = "";
    msg += ";\n changed=" + (startStamp != myModificationsCount);
    msg += ";\n buffer=" + text;
    msg += ";\n this=" + this;
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

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    int curOffset = start;
    for (TreeElement child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      curOffset = child.textMatches(buffer, curOffset);
      if (curOffset < 0) return curOffset;
    }
    return curOffset;
  }

  /*
  protected int textMatches(final CharSequence buffer, final int start) {
    final int[] curOffset = {start};
    acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        matchText(leaf);
      }

      private void matchText(TreeElement leaf) {
        curOffset[0] = leaf.textMatches(buffer, curOffset[0]);
        if (curOffset[0] == -1) {
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
  */

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

  public int countChildren(IElementType type) {
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

    synchronized (START_OFFSET_LOCK) {
      cachedLength = myCachedLength;
      if (cachedLength >= 0) return cachedLength;

      ApplicationManager.getApplication().assertReadAccessAllowed(); //otherwise a write action can modify the tree while we're walking it
      try {
        walkCachingLength();
      }
      catch (AssertionError e) {
        myCachedLength = NOT_CACHED;
        String assertion = StringUtil.getThrowableText(e);
        throw new AssertionError("Walking failure: ===\n"+assertion+"\n=== Thread dump:\n"+ ThreadDumper.dumpThreadsToString()+"\n===\n");
      }
      return myCachedLength;
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

  private void walkCachingLength() {
    if (myCachedLength != NOT_CACHED) {
      throw new AssertionError("Before walking: cached="+myCachedLength);
    }

    TreeElement cur = this;
    while (cur != null) {
      cur = next(cur);
    }

    if (myCachedLength < 0) {
      throw new AssertionError("After walking: cached="+myCachedLength);
    }
  }

  void setCachedLength(int cachedLength) {
    myCachedLength = cachedLength;
  }

  @Nullable
  private TreeElement next(TreeElement cur) {
    //for a collapsed chameleon, we're not going down, even if it's expanded by some other thread after this line
    final int len = cur.getCachedLength();
    final boolean down = len == NOT_CACHED;
    if (down) {
      CompositeElement composite = (CompositeElement)cur; // It's a composite or we won't be going down
      TreeElement child = composite.getFirstChildNode(); // if we're LazyParseable, sync is a must for accessing the non-volatile field
      if (child != null) {
        LOG.assertTrue(child.getTreeParent() == composite, cur);
        return child;
      }

      composite.myCachedLength = 0;
    } else {
      assert len >= 0 : this + "; len=" + len;
    }

    // up
    while (cur != this) {
      CompositeElement parent = cur.getTreeParent();
      int curLength = cur.getCachedLength();
      if (curLength < 0) {
        throw new AssertionError(cur + "; " + curLength);
      }
      parent.myCachedLength -= curLength;

      TreeElement next = cur.getTreeNext();
      if (next != null) {
        LOG.assertTrue(next.getTreePrev() == cur, cur);
        return next;
      }

      LOG.assertTrue(parent.getLastChildNode() == cur, parent);
      parent.myCachedLength = -parent.myCachedLength + NOT_CACHED;

      cur = parent;
    }

    return null;
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

  @Override
  public final PsiElement getPsi() {
    ProgressIndicatorProvider.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    PsiElement wrapper = myWrapper;
    if (wrapper != null) return wrapper;

    synchronized (PsiLock.LOCK) {
      wrapper = myWrapper;
      if (wrapper != null) return wrapper;

      return createAndStorePsi();
    }
  }

  @Override
  @Nullable
  public <T extends PsiElement> T getPsi(Class<T> clazz) {
    return LeafElement.getPsi(clazz, getPsi(), LOG);
  }

  private PsiElement createAndStorePsi() {
    PsiElement psi = createPsiNoLock();
    myWrapper = psi;
    return psi;
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

  public final void rawAddChildren(@NotNull TreeElement first) {
    rawAddChildrenWithoutNotifications(first);

    subtreeChanged();
  }

  public void rawAddChildrenWithoutNotifications(TreeElement first) {
    final TreeElement last = getLastChildNode();
    if (last == null){
      first.rawRemoveUpToWithoutNotifications(null);
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

  // creates PSI and stores to the 'myWrapper', if not created already
  void createAllChildrenPsiIfNecessary() {
    synchronized (PsiLock.LOCK) { // guard for race condition with getPsi()
      acceptTree(CREATE_CHILDREN_PSI);
    }
  }
  private static final RecursiveTreeElementWalkingVisitor CREATE_CHILDREN_PSI = new RecursiveTreeElementWalkingVisitor(false) {
    @Override
    public void visitLeaf(LeafElement leaf) {
    }

    @Override
    public void visitComposite(CompositeElement composite) {
      ProgressIndicatorProvider.checkCanceled(); // we can safely interrupt creating children PSI any moment
      if (composite.myWrapper != null) {
        // someone else 've managed to create the PSI in the meantime. Abandon our attempts to cache everything.
        stopWalking();
        return;
      }
      composite.createAndStorePsi();
      super.visitComposite(composite);
    }
  };

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
