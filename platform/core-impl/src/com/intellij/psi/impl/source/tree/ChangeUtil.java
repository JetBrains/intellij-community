/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ChangeUtil {

  public static void encodeInformation(TreeElement element) {
    encodeInformation(element, element);
  }

  private static void encodeInformation(TreeElement element, ASTNode original) {
    DebugUtil.startPsiModification(null);
    try {
      encodeInformation(element, original, new HashMap<Object, Object>());
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  private static void encodeInformation(TreeElement element, ASTNode original, Map<Object, Object> state) {
    for (TreeCopyHandler handler : Extensions.getExtensions(TreeCopyHandler.EP_NAME)) {
      handler.encodeInformation(element, original, state);
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
    DebugUtil.startPsiModification(null);
    try {
      return decodeInformation(element, new HashMap<Object, Object>());
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  private static TreeElement decodeInformation(TreeElement element, Map<Object, Object> state) {
    TreeElement child = element.getFirstChildNode();
    while (child != null) {
      child = decodeInformation(child, state);
      child = child.getTreeNext();
    }

    for (TreeCopyHandler handler : Extensions.getExtensions(TreeCopyHandler.EP_NAME)) {
      final TreeElement handled = handler.decodeInformation(element, state);
      if (handled != null) return handled;
    }

    return element;
  }

  @NotNull
  public static LeafElement copyLeafWithText(@NotNull LeafElement original, @NotNull String text) {
    LeafElement element = ASTFactory.leaf(original.getElementType(), text);
    original.copyCopyableDataTo(element);
    encodeInformation(element, original);
    TreeUtil.clearCaches(element);
    saveIndentationToCopy(original, element);
    return element;
  }

  public static TreeElement copyElement(@NotNull TreeElement original, CharTable table) {
    CompositeElement treeParent = original.getTreeParent();
    return copyElement(original, treeParent == null ? null : treeParent.getPsi(), table);
  }

  public static TreeElement copyElement(TreeElement original, final PsiElement context, CharTable table) {
    final TreeElement element = (TreeElement)original.clone();
    final PsiManager manager = original.getManager();
    DummyHolderFactory.createHolder(manager, element, context, table).getTreeElement();
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
    final DummyHolder holder = DummyHolderFactory.createHolder(original.getManager(), null, original.getLanguage());
    final FileElement holderElement = holder.getTreeElement();
    final TreeElement treeElement = generateTreeElement(original, holderElement.getCharTable(), original.getManager());
    //  TreeElement treePrev = treeElement.getTreePrev(); // This is hack to support bug used in formater
    holderElement.rawAddChildren(treeElement);
    TreeUtil.clearCaches(holderElement);
    //  treeElement.setTreePrev(treePrev);
    saveIndentationToCopy((TreeElement)original.getNode(), treeElement);
    return treeElement;
  }

  @Nullable
  public static TreeElement generateTreeElement(@Nullable PsiElement original, @NotNull CharTable table, @NotNull final PsiManager manager) {
    if (original == null) return null;
    PsiUtilCore.ensureValid(original);
    if (SourceTreeToPsiMap.hasTreeElement(original)) {
      return copyElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(original), table);
    }
    else {
      for (TreeGenerator generator : Extensions.getExtensions(TreeGenerator.EP_NAME)) {
        final TreeElement element = generator.generateTreeFor(original, table, manager);
        if (element != null) return element;
      }
      return null;
    }
  }

  public static void prepareAndRunChangeAction(final ChangeAction action, final TreeElement changedElement){
    final FileElement changedFile = TreeUtil.getFileElement(changedElement);
    final PsiManager manager = changedFile.getManager();
    final PomModel model = PomManager.getModel(manager.getProject());
    final TreeAspect treeAspect = model.getModelAspect(TreeAspect.class);
    model.runTransaction(new PomTransactionBase(changedElement.getPsi(), treeAspect) {
      @Override
      public PomModelEvent runInner() {
        final PomModelEvent event = new PomModelEvent(model);
        final TreeChangeEvent destinationTreeChange = new TreeChangeEventImpl(treeAspect, changedFile);
        event.registerChangeSet(treeAspect, destinationTreeChange);
        action.makeChange(destinationTreeChange);

        TreeUtil.clearCaches(changedElement);
        if (changedElement instanceof CompositeElement) {
          ((CompositeElement) changedElement).subtreeChanged();
        }
        return event;
      }
    });
  }

  public interface ChangeAction{
    void makeChange(TreeChangeEvent destinationTreeChange);
  }
}
