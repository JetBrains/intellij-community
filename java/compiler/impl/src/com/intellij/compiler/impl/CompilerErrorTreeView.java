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
package com.intellij.compiler.impl;

import com.intellij.codeInsight.daemon.impl.actions.SuppressFix;
import com.intellij.codeInsight.daemon.impl.actions.SuppressForClassFix;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.HelpID;
import com.intellij.compiler.options.CompilerConfigurable;
import com.intellij.ide.errorTreeView.*;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CompilerErrorTreeView extends NewErrorTreeViewPanel {
  public CompilerErrorTreeView(Project project, Runnable rerunAction) {
    super(project, HelpID.COMPILER, true, true, rerunAction);
  }

  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    super.fillRightToolbarGroup(group);
    group.add(new CompilerPropertiesAction());
  }

  protected void addExtraPopupMenuActions(DefaultActionGroup group) {
    group.add(new ExcludeFromCompileAction());
    group.addSeparator();
    group.add(new SuppressJavacWarningsAction());
    group.add(new SuppressJavacWarningForClassAction());
    group.addSeparator();
    ActionGroup popupGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_COMPILER_ERROR_VIEW_POPUP);
    if (popupGroup != null) {
      for (AnAction action : popupGroup.getChildren(null)) {
        group.add(action);
      }
    }
  }

  protected boolean shouldShowFirstErrorInEditor() {
    return CompilerWorkspaceConfiguration.getInstance(myProject).AUTO_SHOW_ERRORS_IN_EDITOR;
  }

  private static class CompilerPropertiesAction extends AnAction {
    private static final Icon ICON_OPTIONS = IconLoader.getIcon("/general/ideOptions.png");

    public CompilerPropertiesAction() {
      super(CompilerBundle.message("action.compiler.properties.text"), null, ICON_OPTIONS);
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      ShowSettingsUtil.getInstance().editConfigurable(project, CompilerConfigurable.getInstance(project));
    }
  }

  private class ExcludeFromCompileAction extends AnAction {
    public ExcludeFromCompileAction() {
      super(CompilerBundle.message("actions.exclude.from.compile.text"));
    }

    public void actionPerformed(AnActionEvent e) {
      VirtualFile file = getSelectedFile();

      if (file != null && file.isValid()) {
        ExcludeEntryDescription description = new ExcludeEntryDescription(file, false, true, myProject);
        ((CompilerConfigurationImpl) CompilerConfiguration.getInstance(myProject)).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
        FileStatusManager.getInstance(myProject).fileStatusesChanged();
      }
    }

    private VirtualFile getSelectedFile() {
      final ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
      if (selectedElement == null) return null;

      String filePresentableText = getSelectedFilePresentableText(selectedElement);

      if (filePresentableText == null) return null;

      return LocalFileSystem.getInstance().findFileByPath(filePresentableText.replace('\\', '/'));
    }

    private String getSelectedFilePresentableText(final ErrorTreeElement selectedElement) {
      String filePresentableText = null;

      if (selectedElement instanceof GroupingElement) {
        GroupingElement groupingElement = (GroupingElement)selectedElement;
        filePresentableText = groupingElement.getName();
      }
      else {
        NodeDescriptor parentDescriptor = getSelectedNodeDescriptor().getParentDescriptor();
        if (parentDescriptor instanceof ErrorTreeNodeDescriptor) {
          ErrorTreeNodeDescriptor treeNodeDescriptor = (ErrorTreeNodeDescriptor)parentDescriptor;
          ErrorTreeElement element = treeNodeDescriptor.getElement();
          if (element instanceof GroupingElement) {
            GroupingElement groupingElement = (GroupingElement)element;
            filePresentableText = groupingElement.getName();
          }
        }

      }
      return filePresentableText;
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final boolean isApplicable = getSelectedFile() != null;
      presentation.setEnabled(isApplicable);
      presentation.setVisible(isApplicable);
    }
  }

  private class SuppressJavacWarningsAction extends AnAction {
    public void actionPerformed(final AnActionEvent e) {
      final NavigatableMessageElement messageElement = (NavigatableMessageElement)getSelectedErrorTreeElement();
      final String[] text = messageElement.getText();
      final String id = text[0].substring(1, text[0].indexOf("]"));
      final SuppressFix suppressInspectionFix = getSuppressAction(id);
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      assert project != null;
      final OpenFileDescriptor navigatable = (OpenFileDescriptor)messageElement.getNavigatable();
      final PsiFile file = PsiManager.getInstance(project).findFile(navigatable.getFile());
      assert file != null;
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                suppressInspectionFix.invoke(project, null, file.findElementAt(navigatable.getOffset()));
              }
              catch (IncorrectOperationException e1) {
                LOG.error(e1);
              }
            }
          });
        }
      }, suppressInspectionFix.getText(), null);
    }

    @Override
    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      presentation.setVisible(false);
      presentation.setEnabled(false);
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;
      final ErrorTreeElement errorTreeElement = getSelectedErrorTreeElement();
      if (errorTreeElement instanceof NavigatableMessageElement) {
        final NavigatableMessageElement messageElement = (NavigatableMessageElement)errorTreeElement;
        final String[] text = messageElement.getText();
        if (text.length > 0) {
          if (text[0].startsWith("[") && text[0].indexOf("]") != -1) {
            presentation.setVisible(true);
            final Navigatable navigatable = messageElement.getNavigatable();
            if (navigatable instanceof OpenFileDescriptor) {
              final OpenFileDescriptor fileDescriptor = (OpenFileDescriptor)navigatable;
              final VirtualFile virtualFile = fileDescriptor.getFile();
              final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
              if (module == null) return;
              final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
              if (jdk == null) return;
              final boolean is_1_5 = JavaSdk.getInstance().compareTo(jdk.getVersionString(), "1.5") >= 0;
              if (!is_1_5) return;
              final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
              if (psiFile == null) return;
              if (LanguageLevelUtil.getEffectiveLanguageLevel(module).compareTo(LanguageLevel.JDK_1_5) < 0) return;
              final PsiElement context = psiFile.findElementAt(fileDescriptor.getOffset());
              if (context == null) return;
              final String id = text[0].substring(1, text[0].indexOf("]"));
              final SuppressFix suppressInspectionFix = getSuppressAction(id);
              final boolean available = suppressInspectionFix.isAvailable(project, null, context);
              presentation.setEnabled(available);
              if (available) {
                presentation.setText(suppressInspectionFix.getText());
              }
            }
          }
        }
      }
    }

    protected SuppressFix getSuppressAction(final String id) {
      return new SuppressFix(id) {
        @Override
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement context) {
          if (getContainer(context) instanceof PsiClass) return false;
          return super.isAvailable(project, editor, context);
        }

        @Override
        protected boolean use15Suppressions(final PsiDocCommentOwner container) {
          return true;
        }
      };
    }
  }

  private class SuppressJavacWarningForClassAction extends SuppressJavacWarningsAction {
    @Override
    protected SuppressFix getSuppressAction(final String id) {
      return new SuppressForClassFix(id){
        @Override
        protected boolean use15Suppressions(final PsiDocCommentOwner container) {
          return true;
        }
      };
    }
  }
}
