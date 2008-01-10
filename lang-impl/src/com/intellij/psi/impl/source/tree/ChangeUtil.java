package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChangeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.ChangeUtil");
  private static final List<TreeCopyHandler> ourCopyHandlers = new CopyOnWriteArrayList<TreeCopyHandler>();
  private static final List<TreeGenerator> ourTreeGenerators = new CopyOnWriteArrayList<TreeGenerator>();

  private ChangeUtil() { }

  public static void registerCopyHandler(TreeCopyHandler handler) {
    ourCopyHandlers.add(handler);
  }

  public static void registerTreeGenerator(TreeGenerator generator) {
    ourTreeGenerators.add(generator);
  }

  public static void addChild(final CompositeElement parent, TreeElement child, final TreeElement anchorBefore) {
    LOG.assertTrue(anchorBefore == null || anchorBefore.getTreeParent() == parent, "anchorBefore == null || anchorBefore.getTreeParent() == parent");
    transformAll(parent.getFirstChildNode());
    final TreeElement last = child.getTreeNext();
    final TreeElement first = transformAll(child);

    final CharTable newCharTab = SharedImplUtil.findCharTableByTree(parent);
    final CharTable oldCharTab = SharedImplUtil.findCharTableByTree(child);

    removeChildrenInner(first, last, oldCharTab);

    if (newCharTab != oldCharTab) registerLeafsInCharTab(newCharTab, child, oldCharTab);

    prepareAndRunChangeAction(new ChangeAction(){
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        if (anchorBefore != null) {
          insertBefore(destinationTreeChange, anchorBefore, first);
        }
        else {
          add(destinationTreeChange, parent, first);
        }
      }
    }, parent);
  }

  public static void removeChild(final CompositeElement parent, final TreeElement child) {
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(parent);
    removeChildInner(child, charTableByTree);
  }

  public static void removeChildren(final CompositeElement parent, final TreeElement first, final TreeElement last) {
    if(first == null) return;
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(parent);
    removeChildrenInner(first, last, charTableByTree);
  }

  public static void replaceChild(final CompositeElement parent, @NotNull final TreeElement old, @NotNull final TreeElement newC) {
    LOG.assertTrue(old.getTreeParent() == parent);
    final TreeElement oldChild = transformAll(old);
    final TreeElement newChildNext = newC.getTreeNext();
    final TreeElement newChild = transformAll(newC);

    if(oldChild == newChild) return;
    final CharTable newCharTable = SharedImplUtil.findCharTableByTree(parent);
    final CharTable oldCharTable = SharedImplUtil.findCharTableByTree(newChild);

    removeChildrenInner(newChild, newChildNext, oldCharTable);

    if (oldCharTable != newCharTable) registerLeafsInCharTab(newCharTable, newChild, oldCharTable);

    prepareAndRunChangeAction(new ChangeAction(){
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        replace(destinationTreeChange, oldChild, newChild);
        repairRemovedElement(parent, newCharTable, oldChild);
      }
    }, parent);
  }

  public static void replaceAllChildren(final CompositeElement parent, final ASTNode newChildrenParent) {
    transformAll(parent.getFirstChildNode());
    transformAll((TreeElement)newChildrenParent.getFirstChildNode());

    final CharTable newCharTab = SharedImplUtil.findCharTableByTree(parent);
    final CharTable oldCharTab = SharedImplUtil.findCharTableByTree(newChildrenParent);

    final ASTNode firstChild = newChildrenParent.getFirstChildNode();
    prepareAndRunChangeAction(new ChangeAction(){
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        destinationTreeChange.addElementaryChange(newChildrenParent, ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, newChildrenParent));
        TreeUtil.removeRange((TreeElement)newChildrenParent.getFirstChildNode(), null);
      }
    }, (CompositeElement)newChildrenParent);

    if (firstChild != null) {
      registerLeafsInCharTab(newCharTab, firstChild, oldCharTab);
      prepareAndRunChangeAction(new ChangeAction(){
        public void makeChange(TreeChangeEvent destinationTreeChange) {
          if(parent.getTreeParent() != null){
            final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, parent);
            changeInfo.setOldLength(parent.getTextLength());
            destinationTreeChange.addElementaryChange(parent, changeInfo);
            TreeUtil.removeRange(parent.getFirstChildNode(), null);
            TreeUtil.addChildren(parent, (TreeElement)firstChild);
          }
          else{
            final TreeElement first = parent.getFirstChildNode();
            remove(destinationTreeChange, first, null);
            add(destinationTreeChange, parent, (TreeElement)firstChild);
            repairRemovedElement(parent, newCharTab, first);
          }
        }
      }, parent);
    }
    else {
      removeChildren(parent, parent.getFirstChildNode(), null);
    }
  }

  private static TreeElement transformAll(TreeElement first){
    ASTNode newFirst = null;
    ASTNode child = first;
    while (child != null) {
      if (child instanceof ChameleonElement) {
        ASTNode next = child.getTreeNext();
        child = ChameleonTransforming.transform((ChameleonElement)child);
        if (child == null) {
          child = next;
        }
        continue;
      }
      if(newFirst == null) newFirst = child;
      child = child.getTreeNext();
    }
    return (TreeElement)newFirst;
  }

  private static void repairRemovedElement(final CompositeElement oldParent, final CharTable newCharTable, final TreeElement oldChild) {
    if(oldChild == null) return;
    final FileElement treeElement = new JavaDummyHolder(oldParent.getManager(), newCharTable, false).getTreeElement();
    TreeUtil.addChildren(treeElement, oldChild);
  }

  private static void add(final TreeChangeEvent destinationTreeChange,
                          final CompositeElement parent,
                          final TreeElement first) {
    TreeUtil.addChildren(parent, first);
    TreeElement child = first;
    while(child != null){
      destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.ADD, child));
      child = child.getTreeNext();
    }
  }

  private static void remove(final TreeChangeEvent destinationTreeChange,
                             final TreeElement first,
                             final TreeElement last) {
    TreeElement child = first;
    while(child != last && child != null){
      destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
      child = child.getTreeNext();
    }
    TreeUtil.removeRange(first, last);
  }

  private static void insertBefore(final TreeChangeEvent destinationTreeChange,
                                   final TreeElement anchorBefore,
                                   final TreeElement first) {
    TreeUtil.insertBefore(anchorBefore, first);
    TreeElement child = first;
    while(child != anchorBefore){
      destinationTreeChange.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.ADD, child));
      child = child.getTreeNext();
    }
  }

  private static void replace(final TreeChangeEvent sourceTreeChange,
                              final TreeElement oldChild,
                              final TreeElement newChild) {
    TreeUtil.replaceWithList(oldChild, newChild);
    final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newChild);
    sourceTreeChange.addElementaryChange(newChild, change);
    change.setReplaced(oldChild);
  }

  private static void registerLeafsInCharTab(CharTable newCharTab, ASTNode child, CharTable oldCharTab) {
    if (newCharTab == oldCharTab) return;
    while (child != null) {
      CharTable charTable = child.getUserData(CharTable.CHAR_TABLE_KEY);
      if (child instanceof LeafElement) {
          ((LeafElement)child).registerInCharTable(newCharTab);
          ((LeafElement)child).registerInCharTable(newCharTab);
        ((LeafElement)child).registerInCharTable(newCharTab);
      }
      else {
        registerLeafsInCharTab(newCharTab, child.getFirstChildNode(), charTable != null ? charTable : oldCharTab);
      }
      if (charTable != null) {
        child.putUserData(CharTable.CHAR_TABLE_KEY, null);
      }
      child = child.getTreeNext();
    }
  }

  private static void removeChildInner(final TreeElement child, final CharTable oldCharTab) {
    removeChildrenInner(child, child.getTreeNext(), oldCharTab);
  }

  private static void removeChildrenInner(final TreeElement first, final TreeElement last, final CharTable oldCharTab) {
    final FileElement fileElement = TreeUtil.getFileElement(first);
    if (fileElement != null) {
      prepareAndRunChangeAction(new ChangeAction() {
        public void makeChange(TreeChangeEvent destinationTreeChange) {
          remove(destinationTreeChange, first, last);
          repairRemovedElement(fileElement, oldCharTab, first);
        }
      }, first.getTreeParent());
    }
    else {
      TreeUtil.removeRange(first, last);
    }
  }

  public static void changeElementInPlace(final ASTNode element, final ChangeAction action){
    prepareAndRunChangeAction(new ChangeAction() {
      public void makeChange(TreeChangeEvent destinationTreeChange) {
        destinationTreeChange.addElementaryChange(element, ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, element));
        action.makeChange(destinationTreeChange);
      }
    }, (TreeElement) element);
    ASTNode node = element;
    while (node != null) {
      ASTNode parent = node.getTreeParent();
      ((TreeElement) node).clearCaches();
      node = parent;
    }
  }

  public static interface ChangeAction{
    void makeChange(TreeChangeEvent destinationTreeChange);
  }

  private static void prepareAndRunChangeAction(final ChangeAction action, final TreeElement changedElement){
    final FileElement changedFile = TreeUtil.getFileElement(changedElement);
    final PsiManager manager = changedFile.getManager();
    final PomModel model = PomManager.getModel(manager.getProject());
    try{
      final TreeAspect treeAspect = model.getModelAspect(TreeAspect.class);
      model.runTransaction(new PomTransactionBase(changedElement.getPsi(), treeAspect) {
        public PomModelEvent runInner() {
          final PomModelEvent event = new PomModelEvent(model);
          final TreeChangeEvent destinationTreeChange = new TreeChangeEventImpl(treeAspect, changedFile);
          event.registerChangeSet(treeAspect, destinationTreeChange);
          final PsiManagerEx psiManager = (PsiManagerEx) manager;
          RepositoryManager repositoryManager = psiManager.getRepositoryManager();
          final PsiFile file = (PsiFile)changedFile.getPsi();

          if (file.isPhysical()) {
            SmartPointerManagerImpl.fastenBelts(file);
          }

          if (repositoryManager != null) {
            repositoryManager.beforeChildAddedOrRemoved(file, changedElement);
            action.makeChange(destinationTreeChange);
            repositoryManager.beforeChildAddedOrRemoved(file, changedElement);
          }
          else {
            action.makeChange(destinationTreeChange);
          }
          psiManager.invalidateFile(file);
          TreeUtil.clearCaches(changedElement);
          if (changedElement instanceof CompositeElement) {
            ((CompositeElement) changedElement).subtreeChanged();
          }
          return event;
        }
      });
    }
    catch(IncorrectOperationException ioe){
      LOG.error(ioe);
    }
  }

  public static void encodeInformation(TreeElement element) {
    encodeInformation(element, element);
  }

  private static void encodeInformation(TreeElement element, ASTNode original) {
    encodeInformation(element, original, new HashMap<Object, Object>());
  }

  private static void encodeInformation(TreeElement element, ASTNode original, Map<Object, Object> state) {
    for (TreeCopyHandler handler : ourCopyHandlers) {
      handler.encodeInformation(element, original, state);
    }

    if (original instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(element);
      ChameleonTransforming.transformChildren(original);
      TreeElement child = element.getFirstChildNode();
      ASTNode child1 = original.getFirstChildNode();
      while (child != null) {
        encodeInformation(child, child1, state);
        child = child.getTreeNext();
        child1 = child1.getTreeNext();
      }
    }
  }

  public static TreeElement decodeInformation(TreeElement element) {
    return decodeInformation(element, new HashMap<Object, Object>());
  }

  private static TreeElement decodeInformation(TreeElement element, Map<Object, Object> state) {
    if (element instanceof CompositeElement) {
      ChameleonTransforming.transformChildren(element);
      TreeElement child = element.getFirstChildNode();
      while (child != null) {
        child = decodeInformation(child, state);
        child = child.getTreeNext();
      }

    }

    for (TreeCopyHandler handler : ourCopyHandlers) {
      final TreeElement handled = handler.decodeInformation(element, state);
      if (handled != null) return handled;
    }

    return element;
  }

  public static TreeElement copyElement(TreeElement original, CharTable table) {
    final TreeElement element = (TreeElement)original.clone();
    final PsiManager manager = original.getManager();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(original);
    registerLeafsInCharTab(table, element, charTableByTree);
    CompositeElement treeParent = original.getTreeParent();
    new JavaDummyHolder(manager, element, treeParent == null ? null : treeParent.getPsi(), table).getTreeElement();
    encodeInformation(element, original);
    TreeUtil.clearCaches(element);
    saveIndentationToCopy(original, element);
    return element;
  }

  private static void saveIndentationToCopy(final TreeElement original, final TreeElement element) {
    if(original == null || element == null || CodeEditUtil.isNodeGenerated(original)) return;
    final int indentation = CodeEditUtil.getOldIndentation(original);
    if(indentation < 0) CodeEditUtil.saveWhitespacesInfo(original);
    CodeEditUtil.setOldIndentation(element, CodeEditUtil.getOldIndentation(original));
    if(indentation < 0) CodeEditUtil.setOldIndentation(original, -1);
  }

  public static TreeElement copyToElement(PsiElement original) {
    final JavaDummyHolder holder = new JavaDummyHolder(original.getManager(), null);
    holder.setLanguage(original.getLanguage());
    final FileElement holderElement = holder.getTreeElement();
    final TreeElement treeElement = generateTreeElement(original, holderElement.getCharTable(), original.getManager());
    //  TreeElement treePrev = treeElement.getTreePrev(); // This is hack to support bug used in formater
    TreeUtil.addChildren(holderElement, treeElement);
    TreeUtil.clearCaches(holderElement);
    //  treeElement.setTreePrev(treePrev);
    saveIndentationToCopy((TreeElement)original.getNode(), treeElement);
    return treeElement;
  }

  @Nullable
  public static TreeElement generateTreeElement(PsiElement original, CharTable table, final PsiManager manager) {
    LOG.assertTrue(original.isValid());
    if (SourceTreeToPsiMap.hasTreeElement(original)) {
      return copyElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    else {
      for (TreeGenerator generator : ourTreeGenerators) {
        final TreeElement element = generator.generateTreeFor(original, table, manager);
        if (element != null) return element;
      }
      return null;
    }
  }

  public static void addChildren(final ASTNode parent,
                                 ASTNode firstChild,
                                 final ASTNode lastChild,
                                 final ASTNode anchorBefore) {
    while (firstChild != lastChild) {
      final ASTNode next = firstChild.getTreeNext();
      parent.addChild(firstChild, anchorBefore);
      firstChild = next;
    }
  }
}
