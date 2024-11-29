// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ChangeUtil {

  private static final Logger LOG = Logger.getInstance(ChangeUtil.class);

  public static void encodeInformation(TreeElement element) {
    encodeInformation(element, element);
  }

  private static void encodeInformation(TreeElement element, ASTNode original) {
    DebugUtil.performPsiModification(null, () -> encodeInformation(element, original, new HashMap<>()));
  }

  private static void encodeInformation(TreeElement element, ASTNode original, Map<Object, Object> state) {
    for (TreeCopyHandler handler : TreeCopyHandler.EP_NAME.getExtensionList()) {
      try {
        handler.encodeInformation(element, original, state);
      }
      catch (IndexNotReadyException ignore) { }
    }

    if (original instanceof CompositeElement) {
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
    return DebugUtil.performPsiModification(null, () -> decodeInformation(element, new HashMap<>()));
  }

  private static TreeElement decodeInformation(TreeElement element, Map<Object, Object> state) {
    TreeElement child = element.getFirstChildNode();
    while (child != null) {
      child = decodeInformation(child, state);
      child = child.getTreeNext();
    }

    for (TreeCopyHandler handler : TreeCopyHandler.EP_NAME.getExtensionList()) {
      TreeElement treeElement = handler.decodeInformation(element, state);
      if (treeElement != null) {
        return treeElement;
      }
    }
    return element;
  }

  public static @NotNull LeafElement copyLeafWithText(@NotNull LeafElement original, @NotNull String text) {
    LeafElement element = ASTFactory.leaf(original.getElementType(), text);
    original.copyCopyableDataTo(element);
    encodeInformation(element, original);
    TreeUtil.clearCaches(element);
    saveIndentationToCopy(original, element);
    return element;
  }

  public static @NotNull TreeElement copyElement(@NotNull TreeElement original, CharTable table) {
    CompositeElement treeParent = original.getTreeParent();
    return copyElement(original, treeParent == null ? null : treeParent.getPsi(), table);
  }

  public static @NotNull TreeElement copyElement(@NotNull TreeElement original, PsiElement context, CharTable table) {
    TreeElement element = (TreeElement)original.clone();
    PsiManager manager = original.getManager();
    DummyHolderFactory.createHolder(manager, element, context, table).getTreeElement();
    encodeInformation(element, original);
    TreeUtil.clearCaches(element);
    saveIndentationToCopy(original, element);
    return element;
  }

  private static void saveIndentationToCopy(TreeElement original, TreeElement element) {
    if(original == null || element == null || CodeEditUtil.isNodeGenerated(original)) return;
    int indentation = CodeEditUtil.getOldIndentation(original);
    if(indentation < 0) CodeEditUtil.saveWhitespacesInfo(original);
    CodeEditUtil.setOldIndentation(element, CodeEditUtil.getOldIndentation(original));
    if(indentation < 0) CodeEditUtil.setOldIndentation(original, -1);
  }

  public static @NotNull TreeElement copyToElement(@NotNull PsiElement original) {
    DummyHolder holder = DummyHolderFactory.createHolder(original.getManager(), null, original.getLanguage());
    FileElement holderElement = holder.getTreeElement();
    TreeElement treeElement = generateTreeElement(original, holderElement.getCharTable(), original.getManager());
    //  TreeElement treePrev = treeElement.getTreePrev(); // This is hack to support bug used in formater
    LOG.assertTrue(
      treeElement != null,
      "original element class: " + original.getClass().getName() + ", language: " + original.getLanguage()
    );
    holderElement.rawAddChildren(treeElement);
    TreeUtil.clearCaches(holderElement);
    //  treeElement.setTreePrev(treePrev);
    saveIndentationToCopy((TreeElement)original.getNode(), treeElement);
    return treeElement;
  }

  public static @Nullable TreeElement generateTreeElement(@Nullable PsiElement original, @NotNull CharTable table, @NotNull PsiManager manager) {
    if (original == null) return null;
    PsiUtilCore.ensureValid(original);
    if (SourceTreeToPsiMap.hasTreeElement(original)) {
      return copyElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    return TreeGenerator.EP_NAME.getExtensionList().stream()
      .map(generator -> generator.generateTreeFor(original, table, manager))
      .filter(Objects::nonNull).findFirst().orElse(null);
  }

  public static void prepareAndRunChangeAction(@NotNull ChangeAction action, @NotNull TreeElement changedElement){
    FileElement changedFile = TreeUtil.getFileElement(changedElement);
    PsiManager manager = changedFile.getManager();
    PomModel model = PomManager.getModel(manager.getProject());
    model.runTransaction(new PomTransactionBase(changedElement.getPsi()) {
      @Override
      public @NotNull PomModelEvent runInner() {
        TreeChangeEvent destinationTreeChange = new TreeChangeEventImpl(model.getModelAspect(TreeAspect.class), changedFile);
        PomModelEvent event = new PomModelEvent(model, destinationTreeChange);
        action.makeChange(destinationTreeChange);

        changedElement.clearCaches();
        if (changedElement instanceof CompositeElement) {
          ((CompositeElement) changedElement).subtreeChanged();
        }
        return event;
      }
    });
  }

  @FunctionalInterface
  public interface ChangeAction{
    void makeChange(@NotNull TreeChangeEvent destinationTreeChange);
  }
}
