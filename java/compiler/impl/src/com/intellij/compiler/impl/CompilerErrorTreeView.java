// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.codeInsight.daemon.impl.actions.SuppressFix;
import com.intellij.codeInsight.daemon.impl.actions.SuppressForClassFix;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.errorTreeView.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompilerErrorTreeView extends NewErrorTreeViewPanel {
  public CompilerErrorTreeView(Project project, Runnable rerunAction) {
    super(project, null, true, true, rerunAction);
  }

  @Override
  protected void fillRightToolbarGroup(@NotNull DefaultActionGroup group) {
    super.fillRightToolbarGroup(group);
    group.addSeparator();
    group.add(new CompilerPropertiesAction());
  }

  @Override
  protected void addExtraPopupMenuActions(@NotNull DefaultActionGroup group) {
    group.addSeparator();
    group.add(new ExcludeFromCompileAction(myProject) {
      @Override
      protected @Nullable VirtualFile getFile() {
        return getSelectedFile();
      }
    });
    group.add(new SuppressJavacWarningsAction());
    group.add(new SuppressJavacWarningForClassAction());
    ActionGroup popupGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_COMPILER_ERROR_VIEW_POPUP);
    if (popupGroup != null) {
      for (AnAction action : popupGroup.getChildren(null)) {
        group.add(action);
      }
    }
  }

  @Override
  protected boolean shouldShowFirstErrorInEditor() {
    return CompilerWorkspaceConfiguration.getInstance(myProject).AUTO_SHOW_ERRORS_IN_EDITOR;
  }

  @Override
  protected @NotNull ErrorViewStructure createErrorViewStructure(Project project, boolean canHideWarnings) {
    return new ErrorViewStructure(project, canHideWarnings) {
      @Override
      protected @NotNull GroupingElement createGroupingElement(String groupName, Object data, VirtualFile file) {
        return new GroupingElement(groupName, data, file) {
          @Override
          public boolean isRenderWithBoldFont() {
            return false;
          }
        };
      }
    };
  }

  private class SuppressJavacWarningsAction extends AnAction {
    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      final NavigatableMessageElement messageElement = (NavigatableMessageElement)getSelectedErrorTreeElement();
      final String[] text = messageElement.getText();
      final String id = text[0].substring(1, text[0].indexOf("]"));
      final SuppressFix suppressInspectionFix = getSuppressAction(id);
      final Project project = e.getProject();
      assert project != null;
      final OpenFileDescriptor navigatable = (OpenFileDescriptor)messageElement.getNavigatable();
      final PsiFile file = PsiManager.getInstance(project).findFile(navigatable.getFile());
      assert file != null;
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          suppressInspectionFix.invoke(project, file.findElementAt(navigatable.getOffset()));
        }
        catch (IncorrectOperationException e1) {
          LOG.error(e1);
        }
      }), suppressInspectionFix.getText(), null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(false);
      final Project project = e.getProject();
      if (project == null) {
        return;
      }
      ErrorTreeElement errorTreeElement = e.getUpdateSession()
        .compute(this, "getSelectedErrorTreeElement", ActionUpdateThread.EDT, CompilerErrorTreeView.this::getSelectedErrorTreeElement);

      if (errorTreeElement instanceof NavigatableMessageElement messageElement) {
        final String[] text = messageElement.getText();
        if (text.length > 0) {
          if (text[0].startsWith("[") && text[0].contains("]")) {
            final Navigatable navigatable = messageElement.getNavigatable();
            if (navigatable instanceof OpenFileDescriptor fileDescriptor) {
              final VirtualFile virtualFile = fileDescriptor.getFile();
              final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
              if (module == null) {
                return;
              }
              final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
              if (jdk == null) {
                return;
              }
              final boolean is_1_5 = JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_5);
              if (!is_1_5) {
                return;
              }
              final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
              if (psiFile == null) {
                return;
              }
              if (LanguageLevelUtil.getEffectiveLanguageLevel(module).compareTo(LanguageLevel.JDK_1_5) < 0) return;
              final PsiElement context = psiFile.findElementAt(fileDescriptor.getOffset());
              if (context == null) {
                return;
              }
              final String id = text[0].substring(1, text[0].indexOf("]"));
              final SuppressFix suppressInspectionFix = getSuppressAction(id);
              final boolean available = suppressInspectionFix.isAvailable(project, context);
              presentation.setEnabledAndVisible(available);
              if (available) {
                presentation.setText(suppressInspectionFix.getText());
              }
            }
          }
        }
      }
    }

    protected SuppressFix getSuppressAction(final @NotNull String id) {
      return new SuppressFix(id) {
        @Override
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean isAvailable(final @NotNull Project project, final @NotNull PsiElement context) {
          if (getContainer(context) instanceof PsiClass) return false;
          return super.isAvailable(project, context);
        }

        @Override
        protected boolean use15Suppressions(final @NotNull PsiJavaDocumentedElement container) {
          return true;
        }
      };
    }
  }

  private final class SuppressJavacWarningForClassAction extends SuppressJavacWarningsAction {
    @Override
    protected SuppressFix getSuppressAction(final @NotNull String id) {
      return new SuppressForClassFix(id){
        @Override
        protected boolean use15Suppressions(final @NotNull PsiJavaDocumentedElement container) {
          return true;
        }
      };
    }
  }
}
