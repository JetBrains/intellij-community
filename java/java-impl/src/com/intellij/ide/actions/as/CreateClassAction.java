/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions.as;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

public class CreateClassAction extends JavaCreateTemplateInPackageAction<PsiClass> implements DumbAware {
  private final JavaDirectoryService myJavaDirectoryService;

  public CreateClassAction() {
    super("Java Class", IdeBundle.message("action.create.new.class.description"), PlatformIcons.CLASS_ICON, true);
    myJavaDirectoryService = JavaDirectoryService.getInstance();
  }

  @Override
  protected void buildDialog(Project project, PsiDirectory directory,
                             com.intellij.ide.actions.CreateFileFromTemplateDialog.Builder builder) {
    builder
      .setTitle(IdeBundle.message("action.create.new.class"))
      .addKind("Class", PlatformIcons.CLASS_ICON, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME)
      .addKind("Interface", PlatformIcons.INTERFACE_ICON, JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME);
    if (LanguageLevelProjectExtension.getInstance(project).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
      builder
        .addKind("Enum", PlatformIcons.ENUM_ICON, JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME)
        .addKind("Annotation", PlatformIcons.ANNOTATION_TYPE_ICON, JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
    }

    for (FileTemplate template : FileTemplateManager.getInstance(project).getAllTemplates()) {
      JavaCreateFromTemplateHandler handler = new JavaCreateFromTemplateHandler();
      if (handler.handlesTemplate(template) && JavaCreateFromTemplateHandler.canCreate(directory)) {
        builder.addKind(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
      }
    }

    builder.setValidator(new CreateNewClassDialogValidatorExImpl(project));
  }

  //TODO: Undo any changes to com.intellij.ide.actions.CreateFromTemplateAction for API compatibility reasons.
  @Override
  @NotNull
  protected com.intellij.ide.actions.CreateFileFromTemplateDialog.Builder newBuilder(@NotNull Project project,
                                                                                     @NotNull PsiDirectory defaultDirectory) {
    return CreateFileFromTemplateDialog.newBuilder(project, defaultDirectory);
  }

  @Override
  protected String removeExtension(String templateName, String className) {
    return StringUtil.trimEnd(className, ".java");
  }

  @Override
  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.class");
  }


  @Override
  protected String getActionName(PsiDirectory directory, String newName, String templateName) {
    String packageDirectoryQualifiedName = myJavaDirectoryService.getPackage(directory).getQualifiedName();
    return IdeBundle.message("progress.creating.class", StringUtil.getQualifiedName(packageDirectoryQualifiedName, newName));
  }

  @Override
  protected PsiClass doCreate(PsiDirectory directory, String className, String templateName, Map<String, String> creationOptions)
    throws IncorrectOperationException {
    return myJavaDirectoryService.createClass(directory, className, templateName, creationOptions, true);
  }

  @Override
  protected PsiElement getNavigationElement(@NotNull PsiClass createdElement) {
    return createdElement.getLBrace();
  }

  @Override
  // TODO: Remove the AnActionEvent parameter, as part of the API compatability fix.
  protected void postProcess(PsiClass createdElement,
                             String templateName,
                             @NotNull Map<String, String> customProperties,
                             @NotNull AnActionEvent e) {
    super.postProcess(createdElement, templateName, customProperties, e);
    moveCaretAfterNameIdentifier(createdElement);
    showOverridesDialog(customProperties, e);
  }

  private void showOverridesDialog(@NotNull Map<String, String> customProperties, @NotNull AnActionEvent e) {
    if (Boolean.TRUE.toString().toUpperCase(Locale.ROOT).equals(customProperties.get(FileTemplate.ATTRIBUTE_SHOW_OVERRIDES_DIALOG))) {
      Editor editor = FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor();
      if (editor instanceof EditorEx) {
        EditorEx editorEx = (EditorEx)editor;
        AnActionEvent event = new AnActionEvent(e.getInputEvent(), editorEx.getDataContext(), ActionPlaces.UNKNOWN,
                                                e.getPresentation(), e.getActionManager(), 0);
        ActionManager.getInstance().getAction("OverrideMethods").actionPerformed(event);
      }
    }
  }
}
