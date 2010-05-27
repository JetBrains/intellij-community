/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.extapi.psi.ASTDelegatePsiElement;
import com.intellij.lang.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiElementArrayConstructor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayCharSequence;
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

  public CompositeElement(@NotNull IElementType type) {
    super(type);
  }

  public int getModificationCount() {
    return myModificationsCount;
  }

  public Object clone() {
    CompositeElement clone = (CompositeElement)super.clone();

    clone.clearCaches();
    clone.firstChild = null;
    clone.lastChild = null;
    clone.myModificationsCount = 0;
    clone.myWrapper = null;
    for (ASTNode child = rawFirstChild(); child != null; child = child.getTreeNext()) {
      clone.rawAddChildren((TreeElement)child.clone());
    }
    return clone;
  }

  public void subtreeChanged() {
    CompositeElement compositeElement = this;
    while(compositeElement != null) {
      compositeElement.clearCaches();
      if (!(compositeElement instanceof PsiElement)) {
        final PsiElement psi = compositeElement.getPsi();
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

  public void clearCaches() {
    super.clearCaches();
    myCachedLength = NOT_CACHED;

    myModificationsCount++;
    myHC = -1;
    
    clearRelativeOffsets(rawFirstChild());
  }

  public void acceptTree(TreeElementVisitor visitor) {
    visitor.visitComposite(this);
  }

  public LeafElement findLeafElementAt(int offset) {
    TreeElement child = getFirstChildNode();
    while (child != null) {
      final int textLength = child.getTextLength();
      if (textLength > offset) {
        if (child instanceof ForeignLeafPsiElement) {
          child = child.getTreeNext();
          continue;
        }
        return child.findLeafElementAt(offset);
      }
      offset -= textLength;
      child = child.getTreeNext();
    }
    return null;
  }

  public ASTNode findChildByType(IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    for(ASTNode element = getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

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

  @Nullable
  public ASTNode findChildByType(@NotNull TokenSet typesSet, ASTNode anchor) {
    ASTNode child = anchor;
    while (true) {
      if (child == null) return null;
      if (typesSet.contains(child.getElementType())) return child;
      child = child.getTreeNext();
    }
  }

  @NotNull
  public String getText() {
    char[] buffer = new char[getTextLength()];
    AstBufferUtil.toBuffer(this, buffer, 0);
    return new String(buffer);
  }

  public CharSequence getChars() {
    char[] buffer = new char[getTextLength()];
    AstBufferUtil.toBuffer(this, buffer, 0);
    return new CharArrayCharSequence(buffer); 
  }

  public int getNotCachedLength() {
    int length = 0;
    TreeElement child = getFirstChildNode();
    while(child != null){
      length += child.getNotCachedLength();
      child = child.getTreeNext();
    }
    return length;
  }

  @NotNull
  public char[] textToCharArray() {
    char[] buffer = new char[getTextLength()];
    AstBufferUtil.toBuffer(this, buffer, 0);
    return buffer;
  }

  public boolean textContains(char c) {
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.textContains(c)) return true;
    }
    return false;
  }

  protected int textMatches(CharSequence buffer, int start) {
    int curOffset = start;
    for (TreeElement child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      curOffset = child.textMatches(buffer, curOffset);
      if (curOffset == -1) return -1;
    }
    return curOffset;
  }


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
    else {
      return 0; //ChildRole.NONE;
    }
  }

  public ASTNode[] getChildren(TokenSet filter) {
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
  public <T extends PsiElement> T[] getChildrenAsPsiElements(TokenSet filter, PsiElementArrayConstructor<T> constructor) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    int count = countChildren(filter);
    T[] result = constructor.newPsiElementArray(count);
    if (count == 0) {
      return result;
    }
    int idx = 0;
    for (ASTNode child = getFirstChildNode(); child != null && idx < count; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
        T element = (T)child.getPsi();
        LOG.assertTrue(element != null, child);
        result[idx++] = element;
      }
    }
    return result;
  }

  public int countChildren(TokenSet filter) {
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (filter == null || filter.contains(child.getElementType())) {
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

  public int getTextLength() {
    int cachedLength = myCachedLength;
    if (cachedLength >= 0) return cachedLength;

    synchronized (START_OFFSET_LOCK) {
      cachedLength = myCachedLength;
      if (cachedLength >= 0) return cachedLength;

      walkCachingLength();
      return myCachedLength;
    }
  }

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

  public int getCachedLength() {
    return myCachedLength;
  }

  private void walkCachingLength() {
    TreeElement cur = this;

    while (cur != null) {
      cur = next(cur, cur.getCachedLength() == NOT_CACHED);
    }

    LOG.assertTrue(myCachedLength >= 0, myCachedLength);
  }

  @Nullable
  private TreeElement next(TreeElement cur, boolean down) {
    if (down) {
      CompositeElement composite = (CompositeElement)cur; // It's a composite or we won't be going down
      TreeElement child = composite.firstChild;
      if (child != null) {
        LOG.assertTrue(child.getTreeParent() == composite, cur);
        return child;
      }

      composite.myCachedLength = 0;
    }

    // up
    while (cur != this) {
      CompositeElement parent = cur.getTreeParent();
      int curLength = cur.getCachedLength();
      LOG.assertTrue(curLength != NOT_CACHED, cur);
      parent.myCachedLength -= curLength;

      TreeElement next = cur.getTreeNext();
      if (next != null) {
        LOG.assertTrue(next.getTreePrev() == cur, cur);
        return next;
      }

      LOG.assertTrue(parent.lastChild == cur, parent);
      parent.myCachedLength = -parent.myCachedLength + NOT_CACHED;

      cur = parent;
    }

    return null;
  }


  public TreeElement getFirstChildNode() {
    return firstChild;
  }

  public TreeElement getLastChildNode() {
    return lastChild;
  }

  public void setFirstChildNode(TreeElement firstChild) {
    this.firstChild = firstChild;
  }

  public void setLastChildNode(TreeElement lastChild) {
    this.lastChild = lastChild;
  }

  public void addChild(@NotNull ASTNode child, final ASTNode anchorBefore) {
    LOG.assertTrue(anchorBefore == null || ((TreeElement)anchorBefore).getTreeParent() == this, "anchorBefore == null || anchorBefore.getTreeParent() == parent");
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(child);
    final TreeElement last = ((TreeElement)child).getTreeNext();
    final TreeElement first = (TreeElement)child;

    removeChildrenInner(first, last);

    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
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

  public void addLeaf(@NotNull final IElementType leafType, final CharSequence leafText, final ASTNode anchorBefore) {
    FileElement holder = new DummyHolder(getManager(), null).getTreeElement();
    final LeafElement leaf = ASTFactory.leaf(leafType, holder.getCharTable().intern(leafText));
    CodeEditUtil.setNodeGenerated(leaf, true);
    holder.rawAddChildren(leaf);

    addChild(leaf, anchorBefore);
  }

  public void addChild(@NotNull ASTNode child) {
    addChild(child, null);
  }

  public void removeChild(@NotNull ASTNode child) {
    removeChildInner((TreeElement)child);
  }

  public void removeRange(@NotNull ASTNode first, ASTNode firstWhichStayInTree) {
    removeChildrenInner((TreeElement)first, (TreeElement)firstWhichStayInTree);
  }

  public void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild) {
    LOG.assertTrue(((TreeElement)oldChild).getTreeParent() == this);
    final TreeElement oldChild1 = (TreeElement)oldChild;
    final TreeElement newChildNext = ((TreeElement)newChild).getTreeNext();
    final TreeElement newChild1 = (TreeElement)newChild;

    if(oldChild1 == newChild1) return;

    removeChildrenInner(newChild1, newChildNext);

    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        replace(destinationTreeChange, oldChild1, newChild1);
        repairRemovedElement(CompositeElement.this, oldChild1);
      }
    }, this);
  }

  public void replaceAllChildrenToChildrenOf(final ASTNode anotherParent) {
    TreeUtil.ensureParsed(getFirstChildNode());
    TreeUtil.ensureParsed(anotherParent.getFirstChildNode());
    final ASTNode firstChild1 = anotherParent.getFirstChildNode();
    ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        destinationTreeChange.addElementaryChange(anotherParent, ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, anotherParent));
        ((CompositeElement)anotherParent).rawRemoveAllChildren();
      }
    }, (TreeElement)anotherParent);

    if (firstChild1 != null) {
      ChangeUtil.prepareAndRunChangeAction(new ChangeUtil.ChangeAction(){
        public void makeChange(TreeChangeEvent destinationTreeChange) {
          if(getTreeParent() != null){
            final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, CompositeElement.this);
            changeInfo.setOldLength(getTextLength());
            destinationTreeChange.addElementaryChange(CompositeElement.this, changeInfo);
            rawRemoveAllChildren();
            rawAddChildren((TreeElement)firstChild1);
          }
          else{
            final TreeElement first = getFirstChildNode();
            remove(destinationTreeChange, first, null);
            add(destinationTreeChange, CompositeElement.this, (TreeElement)firstChild1);
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

  public void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore) {
    while (firstChild != lastChild) {
      final ASTNode next1 = firstChild.getTreeNext();
      addChild(firstChild, anchorBefore);
      firstChild = next1;
    }
  }

  public PsiElement getPsi() {
    PsiElement wrapper = myWrapper;
    if (wrapper != null) return wrapper;

    synchronized (PsiLock.LOCK) {
      wrapper = myWrapper;
      if (wrapper != null) return wrapper;

      final Language lang = getElementType().getLanguage();
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      if (parserDefinition != null) {
        myWrapper = wrapper = parserDefinition.createElement(this);
        //noinspection ConstantConditions
        LOG.assertTrue(wrapper != null, "ParserDefinition.createElement() may not return null");
      }

      return wrapper;
    }
  }

  public void setPsi(@NotNull PsiElement psi) {
    myWrapper = psi;
  }

  public void rawAddChildren(@NotNull TreeElement first) {
    final TreeElement last = getLastChildNode();
    if (last == null){
      setFirstChildNode(first);
      first.setTreePrev(null);
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
      last.rawInsertAfterMe(first);
    }

    if (DebugUtil.CHECK) DebugUtil.checkTreeStructure(this);
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
    final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newChild);
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
