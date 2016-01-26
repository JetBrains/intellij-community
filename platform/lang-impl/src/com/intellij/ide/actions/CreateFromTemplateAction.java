/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.google.common.base.Splitter;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class CreateFromTemplateAction<T extends PsiElement> extends AnAction {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateFromTemplateAction");

  public CreateFromTemplateAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final PsiDirectory dir = view.getOrChooseDirectory();
    if (dir == null || project == null) return;

    final CreateFileFromTemplateDialog.Builder builder = newBuilder(project);
    buildDialog(project, dir, builder);

    final Ref<String> selectedTemplateName = Ref.create(null);

    final T createdElement =
      builder.show(getErrorTitle(), getDefaultTemplateName(dir), new CreateFileFromTemplateDialog.FileCreator<T>() {

        @Override
        public T createFile(@NotNull String name, Map<String, String> creationOptions, @NotNull String templateName) {
          selectedTemplateName.set(templateName);
          String enteredPackageName = creationOptions.get(FileTemplate.ATTRIBUTE_PACKAGE_NAME);
          PsiDirectory packageSubdirectory;
          try {
            packageSubdirectory = createPackageSubdirectory(dir, enteredPackageName);
          }
          catch (BadPackageNameException exception) {
            // TODO: Can we somehow present an error to the user at this point?
            packageSubdirectory = dir;
          }

          return CreateFromTemplateAction.this.createFile(name, templateName, creationOptions, packageSubdirectory);
        }

        @Override
        @NotNull
        public String getActionName(@NotNull String name, @NotNull String templateName) {
          return CreateFromTemplateAction.this.getActionName(dir, name, templateName);
        }
      });
    if (createdElement != null) {
      view.selectElement(createdElement);
      postProcess(createdElement, selectedTemplateName.get(), builder.getCustomProperties());
    }
  }

  /**
   * Using the given directory, steps up the directory tree (if needed) to find a common ancestor with the desired package name.
   * Then it builds the subdirectories (if needed) to create a directory for that package name.
   * Example:
   * directory: PsiDirectory "/home/username/androidStudio/myProject/com/example/widget/ui/buttons"
   * packageName: com.example.widget.io.net
   * <ol>
   * <li>com.example.widget.ui.buttons != com.example.widget.io.net && com.example.widget.ui.buttons ⊄ com.example.widget.io.net
   * So remove ".buttons" and step up one directory</li>
   *
   * <li>com.example.widget.ui != com.example.widget.io.net && com.example.widget.ui ⊄ com.example.widget.io.net
   * So remove ".ui" and step up one directory</li>
   *
   * <li>com.example.widget != com.example.widget.io.net BUT com.example.widget ⊂ com.example.widget.io.net
   * So append ".io" and create and enter that directory</li>
   *
   * <li>com.example.widget.io != com.example.widget.io.net BUT com.example.widget.io ⊂ com.example.widget.io.net
   * So append ".net" and create and enter that directory</li>
   *
   * <li>com.example.widget.io.net == com.example.widget.io.net</li>
   * Complete
   * </ol>
   *
   * Why not just start with the com and move down the hierarchy? It requires a PsiDirectory object to build the directories in IJ and
   * on disk and then to pass back to the caller. This requires starting from the known PsiDirectory.
   *
   * @param directory   The directory to start in. Usually the directory of the currently open file or package the user clicked on.
   * @param packageName The name of the package to create a matching directory for.
   * @return A PsiDirectory representing the new directory matching the package.
   */
  @NotNull
  protected PsiDirectory createPackageSubdirectory(@NotNull PsiDirectory directory, @NotNull String packageName)
    throws BadPackageNameException {
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    String startPackagePath = psiPackage != null ? psiPackage.getQualifiedName() : null;

    // If the start directory is the same as the desired one, no work required.
    if (startPackagePath == null || startPackagePath.equals(packageName)) {
      return directory;
    }


    PsiPackage baseName = JavaDirectoryService.getInstance().getPackage(directory);
    PsiDirectory dir = directory;

    if (!packageName.startsWith(baseName.getQualifiedName())) {
      // This means that baseName is not an ancestor of packageName (like in the example above).
      // We need to walk up the package tree from baseName until we get to a common ancestor.
      while (baseName.getParentPackage() != null) {
        if (packageName.equals(baseName.getQualifiedName())) {
          // We've stepped back in baseName and discovered packageName is an ancestor.
          // (E.g. baseName started as com.example.widget.io and packageName is com.example.widget).
          return dir;
        }
        else if (packageName.startsWith(baseName.getQualifiedName())) {
          // We've traversed up the tree from baseName to the point where baseName is now an ancestor of packageName.
          // (E.g. baseName started as com.example.widget.io, but is now com.example.widget and packageName is com.example.widget.ui).
          break;
        }
        else {
          // We still haven't found the common ancestor, so go up one level in the tree.
          baseName = baseName.getParentPackage();
          dir = dir.getParentDirectory();
        }
      }
    }

    if (!packageName.startsWith(baseName.getQualifiedName())) {
      // packageName cannot be derived from baseName (e.g. packageName is broken.com.example.widget.ui, and baseName is com.example.widget).
      // This means the package name is invalid input.
      throw new BadPackageNameException(packageName);
    }

    // baseName is now an ancestor of packageName, so find the intervening nodes and make directories for them if needed.
    for (String component : Splitter.on('.').split(packageName.substring(baseName.getQualifiedName().length() + 1))) {
      PsiDirectory d = dir.findSubdirectory(component);
      dir = d == null ? dir.createSubdirectory(component) : d;
    }

    return dir;
  }

  protected void postProcess(T createdElement, String templateName, Map<String, String> customProperties) {
  }

  @Nullable
  protected abstract T createFile(String name, String templateName, Map<String, String> creationOptions, PsiDirectory dir);

  protected abstract void buildDialog(Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder);

  protected CreateFileFromTemplateDialog.Builder newBuilder(Project project) {
    return CreateFileFromTemplateDialog.newBuilder(project);
  }

  @Nullable
  protected String getDefaultTemplateName(@NotNull PsiDirectory dir) {
    String property = getDefaultTemplateProperty();
    return property == null ? null : PropertiesComponent.getInstance(dir.getProject()).getValue(property);
  }

  @Nullable
  protected String getDefaultTemplateProperty() {
    return null;
  }

  @Override
  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }

  protected boolean isAvailable(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    return project != null && view != null && view.getDirectories().length != 0;
  }

  protected abstract String getActionName(PsiDirectory directory, String newName, String templateName);

  protected String getErrorTitle() {
    return CommonBundle.getErrorTitle();
  }

  //todo append $END variable to templates?
  public static void moveCaretAfterNameIdentifier(PsiNameIdentifierOwner createdElement) {
    final Project project = createdElement.getProject();
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      final VirtualFile virtualFile = createdElement.getContainingFile().getVirtualFile();
      if (virtualFile != null) {
        if (FileDocumentManager.getInstance().getDocument(virtualFile) == editor.getDocument()) {
          final PsiElement nameIdentifier = createdElement.getNameIdentifier();
          if (nameIdentifier != null) {
            editor.getCaretModel().moveToOffset(nameIdentifier.getTextRange().getEndOffset());
          }
        }
      }
    }
  }

  public static class BadPackageNameException extends Exception {
    public BadPackageNameException(String message) {
      super(message);
    }
  }
}
