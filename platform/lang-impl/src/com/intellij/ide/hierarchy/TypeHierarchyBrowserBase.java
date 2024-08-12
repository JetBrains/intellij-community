// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class TypeHierarchyBrowserBase extends HierarchyBrowserBaseEx {
  private boolean myIsInterface;

  private final MyDeleteProvider myDeleteElementProvider = new MyDeleteProvider();

  public TypeHierarchyBrowserBase(Project project, PsiElement element) {
    super(project, element);
  }

  protected abstract boolean isInterface(@NotNull PsiElement psiElement);

  protected void createTreeAndSetupCommonActions(@NotNull Map<? super @Nls String, ? super JTree> trees, @NotNull String groupId) {
    BaseOnThisTypeAction baseOnThisTypeAction = createBaseOnThisAction();
    JTree tree1 = createTree(true);
    PopupHandler.installPopupMenu(tree1, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree1);
    trees.put(getTypeHierarchyType(), tree1);

    JTree tree2 = createTree(true);
    PopupHandler.installPopupMenu(tree2, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree2);
    trees.put(getSupertypesHierarchyType(), tree2);

    JTree tree3 = createTree(true);
    PopupHandler.installPopupMenu(tree3, groupId, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP);
    baseOnThisTypeAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), tree3);
    trees.put(getSubtypesHierarchyType(), tree3);
  }

  protected @NotNull BaseOnThisTypeAction createBaseOnThisAction() {
    return new BaseOnThisTypeAction();
  }

  protected abstract boolean canBeDeleted(PsiElement psiElement);

  protected abstract String getQualifiedName(PsiElement psiElement);

  @Override
  protected @NotNull Map<String, Supplier<String>> getPresentableNameMap() {
    HashMap<String, Supplier<String>> map = new HashMap<>();
    map.put(getTypeHierarchyType(), TypeHierarchyBrowserBase::getTypeHierarchyType);
    map.put(getSubtypesHierarchyType(), TypeHierarchyBrowserBase::getSubtypesHierarchyType);
    map.put(getSupertypesHierarchyType(), TypeHierarchyBrowserBase::getSupertypesHierarchyType);
    return map;
  }

  public boolean isInterface() {
    return myIsInterface;
  }

  @Override
  protected void setHierarchyBase(@NotNull PsiElement element) {
    super.setHierarchyBase(element);
    myIsInterface = isInterface(element);
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new ViewClassHierarchyAction());
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
  }

  @Override
  protected @NotNull String getActionPlace() {
    return ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, myDeleteElementProvider);
  }

  @Override
  protected @NotNull String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.prev.occurence.name");
  }

  @Override
  protected @NotNull String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.next.occurence.name");
  }

  private final class MyDeleteProvider implements DeleteProvider {
    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      PsiElement aClass = getSelectedElement(dataContext);
      if (!canBeDeleted(aClass)) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting.class", getQualifiedName(aClass)));
      try {
        PsiElement[] elements = {aClass};
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      PsiElement aClass = getSelectedElement(dataContext);
      if (!canBeDeleted(aClass)) {
        return false;
      }
      PsiElement[] elements = {aClass};
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
  protected static class BaseOnThisTypeAction extends BaseOnThisElementAction {

    public BaseOnThisTypeAction() {
      super(LanguageTypeHierarchy.INSTANCE);
    }

    @Override
    protected @Nls String correctViewType(@NotNull HierarchyBrowserBaseEx browser, @Nls String viewType) {
      if (((TypeHierarchyBrowserBase)browser).myIsInterface && getTypeHierarchyType().equals(viewType)) {
        return getSubtypesHierarchyType();
      }
      return viewType;
    }
  }

  public static @Nls @NotNull String getTypeHierarchyType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.class");
  }

  public static @Nls @NotNull String getSubtypesHierarchyType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.subtypes");
  }

  public static @Nls @NotNull String getSupertypesHierarchyType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.supertypes");
  }
}
