// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.GeneralModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

public class GeneralModuleTypeForIdea extends GeneralModuleType {
  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return new GeneralModuleBuilder() {
      @Override
      public @NotNull List<Class<? extends ModuleWizardStep>> getIgnoredSteps() {
        return List.of(ProjectSettingsStep.class);
      }

      @Override
      public @NotNull ModuleWizardStep getCustomOptionsStep(WizardContext context,
                                                            Disposable parentDisposable) {
        ProjectSettingsStep step = new ProjectSettingsStep(context);
        step.getExpertPlaceholder().removeAll();
        JTextPane textPane = new JTextPane();
        textPane.setText(getDescription());
        step.getExpertPlaceholder().setMinimumSize(new Dimension(0, 100));
        step.getExpertPlaceholder().add(ScrollPaneFactory.createScrollPane(textPane));
        return step;
      }

      @Override
      public boolean isAvailable() {
        return !isNewWizard();
      }

      @Override
      public ModuleType<?> getModuleType() {
        return GeneralModuleTypeForIdea.this;
      }

      @Override
      public @Nullable List<Module> commit(@NotNull Project project,
                                           ModifiableModuleModel model,
                                           ModulesProvider modulesProvider) {
        List<Module> modules = super.commit(project, model, modulesProvider);
        scheduleTooltip(project);
        return modules;
      }

      private void scheduleTooltip(@NotNull Project project) {
        StartupManager.getInstance(project).runAfterOpened(() -> {
          if (ProjectView.getInstance(project).getCurrentProjectViewPane() != null) {
            showTooltip(project);
            return;
          }
          project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void toolWindowShown(@NotNull ToolWindow toolWindow) {
              if (!"Project".equals(toolWindow.getId())) return;
              showTooltip(project);
            }
          });
        });
      }

      private void showTooltip(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
          JTree tree = ProjectView.getInstance(project).getCurrentProjectViewPane().getTree();
          String shortcutText = KeymapUtil.getShortcutText(IdeActions.ACTION_NEW_ELEMENT);
          GotItTooltip tooltip =
            new GotItTooltip("empty.project.create.file", IdeBundle.message("to.create.new.file.tooltip", shortcutText), project)
              .withPosition(Balloon.Position.atRight).withContrastColors(true);
          ApplicationManager.getApplication().getMessageBus().connect(tooltip).subscribe(AnActionListener.TOPIC, new AnActionListener() {
                                                                                              @Override
                                                                                              public void afterActionPerformed(@NotNull AnAction action,
                                                                                                                               @NotNull AnActionEvent event,
                                                                                                                               @NotNull AnActionResult result) {
                                                                                                tooltip.gotIt();
                                                                                                Disposer.dispose(tooltip);
                                                                                              }
                                                                                            });
          tooltip.show(tree, (component, balloon) -> getPoint(tree));
        });
      }

      private Point getPoint(JTree tree) {
        TreePath path = tree.getSelectionPath();
        Rectangle bounds = tree.getPathBounds(path);
        int x = tree.getVisibleRect().width + 5;
        return bounds == null ? new Point(x, 10) : new Point(x, (int)bounds.getCenterY());
      }
    };
  }

  private static boolean isNewWizard() {
    return Experiments.getInstance().isFeatureEnabled("new.project.wizard");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getDescription() {
    return IdeBundle.message("general.module.type.description");
  }
}
