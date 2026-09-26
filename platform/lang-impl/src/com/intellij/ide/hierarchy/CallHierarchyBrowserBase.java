// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class CallHierarchyBrowserBase extends HierarchyBrowserBaseEx {
  public CallHierarchyBrowserBase(@NotNull Project project, @NotNull PsiElement method) {
    super(project, method);
  }

  @Override
  public void setContent(@NotNull Content content) {
    super.setContent(content);

    myProject.getMessageBus().connect(content).subscribe(
      ToolWindowManagerListener.TOPIC,
      new MyCallHierarchyBrowserListener());
  }

  @Override
  protected @Nullable JPanel createLegendPanel() {
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
  protected @NotNull String getActionPlace() {
    return ActionPlaces.CALL_HIERARCHY_VIEW_TOOLBAR;
  }

  @Override
  protected @NotNull String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.call.prev.occurence.name");
  }

  @Override
  protected @NotNull String getNextOccurenceActionNameImpl() {
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      String currentType = event.getUpdateSession().compute(
        this, "getCurrentViewType", ActionUpdateThread.EDT, () -> getCurrentViewType());
      return myTypeName.equals(currentType);
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

  private class MyCallHierarchyBrowserListener implements ToolWindowManagerListener {
    private boolean myLastVisible = true;
    private @Nullable HierarchyTreeStructure mySavedTreeStructure;
    private @Nullable List<Object> mySavedPathsToExpand;
    private @Nullable List<Object> mySavedSelectionPaths;

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      ToolWindow window = toolWindowManager.getToolWindow(ToolWindowId.HIERARCHY);
      if (window == null || window.isDisposed()) return;

      boolean visible = window.isVisible();
      if (visible == myLastVisible) return;
      myLastVisible = visible;

      if (visible) {
        onToolWindowShown();
      }
      else {
        onToolWindowHidden();
      }
    }

    private void onToolWindowHidden() {
      ThreadingAssertions.assertEventDispatchThread();
      if (isDisposed()) return;

      mySavedTreeStructure = getCurrentTreeStructure();

      List<Object> pathsToExpand = new ArrayList<>();
      List<Object> selectionPaths = new ArrayList<>();
      saveCurrentTreeState(pathsToExpand, selectionPaths);
      mySavedPathsToExpand = pathsToExpand;
      mySavedSelectionPaths = selectionPaths;

      disposeAllSheets();
    }

    private void onToolWindowShown() {
      ThreadingAssertions.assertEventDispatchThread();
      if (isDisposed()) return;

      @Nls String currentViewType = getCurrentViewType();
      if (currentViewType == null || !isValidBase()) return;

      HierarchyTreeStructure savedStructure = mySavedTreeStructure;
      List<Object> pathsToExpand = mySavedPathsToExpand;
      List<Object> selectionPaths = mySavedSelectionPaths;
      mySavedTreeStructure = null;
      mySavedPathsToExpand = null;
      mySavedSelectionPaths = null;

      changeViewWithStructure(currentViewType, false, savedStructure);

      if (pathsToExpand != null && selectionPaths != null) {
        restoreTreeState(pathsToExpand, selectionPaths);
      }
    }
  }
}