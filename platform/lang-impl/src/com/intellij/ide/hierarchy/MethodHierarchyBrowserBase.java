// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class MethodHierarchyBrowserBase extends HierarchyBrowserBaseEx {
  public MethodHierarchyBrowserBase(Project project, PsiElement method) {
    super(project, method);
  }

  @Override
  protected @NotNull String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.method.prev.occurence.name");
  }

  @Override
  protected @NotNull Map<String, Supplier<String>> getPresentableNameMap() {
    Map<String, Supplier<String>> map = new HashMap<>();
    map.put(getMethodType(), MethodHierarchyBrowserBase::getMethodType);
    return map;
  }

  @Override
  protected @NotNull String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.method.next.occurence.name");
  }

  protected static JPanel createStandardLegendPanel(@NlsContexts.Label String methodDefinedText,
                                                    @NlsContexts.Label String methodNotDefinedLegallyText,
                                                    @NlsContexts.Label String methodShouldBeDefined) {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insets(3, 5, 0, 5), 0, 0);

    JLabel label = new JLabel(methodDefinedText, AllIcons.Hierarchy.MethodDefined, SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label = new JLabel(methodNotDefinedLegallyText, AllIcons.Hierarchy.MethodNotDefined, SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label = new JLabel(methodShouldBeDefined, AllIcons.Hierarchy.ShouldDefineMethod, SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    return panel;
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ShowImplementationsOnlyAction());
    actionGroup.add(new ChangeScopeAction());
  }

  @Override
  protected @NotNull String getActionPlace() {
    return ActionPlaces.METHOD_HIERARCHY_VIEW_TOOLBAR;
  }

  private final class ShowImplementationsOnlyAction extends ToggleAction {
    private ShowImplementationsOnlyAction() {
      super(IdeBundle.messagePointer("action.hide.non.implementations"), AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).getState().HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      HierarchyBrowserManager.getInstance(myProject).getState().HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED = flag;
      // invokeLater is called to update state of button before long tree building operation
      ApplicationManager.getApplication().invokeLater(() -> doRefresh(true));
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      super.update(event);
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  public static class BaseOnThisMethodAction extends BaseOnThisElementAction {
    public BaseOnThisMethodAction() {
      super(LanguageMethodHierarchy.INSTANCE);
    }
  }

  public static @Nls String getMethodType() {
    //noinspection UnresolvedPropertyKey
    return IdeBundle.message("title.hierarchy.method");
  }
}
