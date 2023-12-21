// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class CallHierarchyBrowserBase extends HierarchyBrowserBaseEx {
  public CallHierarchyBrowserBase(@NotNull Project project, @NotNull PsiElement method) {
    super(project, method);
  }

  @Override
  @Nullable
  protected JPanel createLegendPanel() {
    return null;
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new ChangeViewTypeActionBase(IdeBundle.message("action.caller.methods.hierarchy"),
                                                 IdeBundle.message("action.caller.methods.hierarchy"),
                                                 AllIcons.Hierarchy.Supertypes, getCallerType()));
    actionGroup.add(new ChangeViewTypeActionBase(IdeBundle.message("action.callee.methods.hierarchy"),
                                                 IdeBundle.message("action.callee.methods.hierarchy"),
                                                 AllIcons.Hierarchy.Subtypes, getCalleeType()));
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ChangeScopeAction());
  }

  @Override
  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.CALL_HIERARCHY_VIEW_TOOLBAR;
  }

  @Override
  @NotNull
  protected String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.call.prev.occurence.name");
  }

  @Override
  @NotNull
  protected String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.call.next.occurence.name");
  }

  @Override
  protected @NotNull Map<String, Supplier<String>> getPresentableNameMap() {
    HashMap<String, Supplier<String>> map = new HashMap<>();
    map.put(getCallerType(), CallHierarchyBrowserBase::getCallerType);
    map.put(getCalleeType(), CallHierarchyBrowserBase::getCalleeType);
    return map;
  }

  private final class ChangeViewTypeActionBase extends ToggleAction {
    private final @Nls String myTypeName;

    private ChangeViewTypeActionBase(@NlsActions.ActionText String shortDescription, @NlsActions.ActionDescription String longDescription, Icon icon, @Nls String typeName) {
      super(shortDescription, longDescription, icon);
      myTypeName = typeName;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return myTypeName.equals(getCurrentViewType());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      if (flag) {
        // invokeLater is called to update state of button before long tree building operation
        ApplicationManager.getApplication().invokeLater(() -> changeView(myTypeName));
      }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      super.update(event);
      setEnabled(isValidBase());
    }
  }

  protected static class BaseOnThisMethodAction extends BaseOnThisElementAction {
    public BaseOnThisMethodAction() {
      super(LanguageCallHierarchy.INSTANCE);
    }
  }

  public static @NotNull @Nls String getCalleeType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.callees.of");
  }

  public static @NotNull @Nls String getCallerType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.callers.of");
  }
}