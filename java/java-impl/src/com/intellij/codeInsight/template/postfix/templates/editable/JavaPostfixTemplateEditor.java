// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateEditorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaPostfixTemplateEditor extends PostfixTemplateEditorBase<JavaPostfixTemplateExpressionCondition> {

  @NotNull private final JPanel myPanel;
  @NotNull private final ComboBox<LanguageLevel> myLanguageLevelCombo;
  @NotNull private final JBCheckBox myStaticImportCheckBox;

  public JavaPostfixTemplateEditor(@NotNull PostfixTemplateProvider provider) {
    super(provider, createEditor(), true);
    myStaticImportCheckBox = new JBCheckBox(JavaBundle.message("dialog.edit.template.checkbox.use.static.import"));
    myLanguageLevelCombo = new ComboBox<>(LanguageLevel.values());
    myLanguageLevelCombo.setRenderer(SimpleListCellRenderer.create("", LanguageLevel::getPresentableText));

    myPanel = FormBuilder.createFormBuilder()
                         .addLabeledComponent(JavaBundle.message("postfix.template.language.level.title"), myLanguageLevelCombo)
                         .addComponentFillVertically(myEditTemplateAndConditionsPanel, UIUtil.DEFAULT_VGAP)
                         .addComponent(myStaticImportCheckBox)
                         .getPanel();
  }

  @NotNull
  private static Editor createEditor() {
    return createEditor(null, createDocument(ProjectManager.getInstance().getDefaultProject()));
  }

  @NotNull
  @Override
  public JavaEditablePostfixTemplate createTemplate(@NotNull String templateId, @NotNull String templateName) {
    LanguageLevel selectedLanguageLevel = ObjectUtils.tryCast(myLanguageLevelCombo.getSelectedItem(), LanguageLevel.class);
    LanguageLevel languageLevel = ObjectUtils.notNull(selectedLanguageLevel, LanguageLevel.JDK_1_3);
    Set<JavaPostfixTemplateExpressionCondition> conditions = new LinkedHashSet<>();
    ContainerUtil.addAll(conditions, myExpressionTypesListModel.elements());
    String templateText = myTemplateEditor.getDocument().getText();
    boolean useTopmostExpression = myApplyToTheTopmostJBCheckBox.isSelected();
    boolean useStaticImport = myStaticImportCheckBox.isSelected();
    JavaEditablePostfixTemplate template =
      new JavaEditablePostfixTemplate(templateId, templateName, templateText, "", conditions, languageLevel, useTopmostExpression,
                                      myProvider);
    template.getLiveTemplate().setValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE, useStaticImport);
    return template;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private static Document createDocument(@Nullable Project project) {
    if (project == null) {
      return EditorFactory.getInstance().createDocument("");
    }
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment("", null, true);
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(fragment, false);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Override
  protected void fillConditions(@NotNull DefaultActionGroup group) {
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition()));
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      group.add(new ChooseClassAction(project));
    }
    group.add(new ChooseClassAction(null));
  }

  @Override
  public void setTemplate(@Nullable PostfixTemplate template) {
    super.setTemplate(template);
    if (template instanceof JavaEditablePostfixTemplate) {
      JavaEditablePostfixTemplate javaTemplate = (JavaEditablePostfixTemplate)template;
      myLanguageLevelCombo.setSelectedItem(javaTemplate.getMinimumLanguageLevel());
      myStaticImportCheckBox.setSelected(javaTemplate.getLiveTemplate().getValue(Template.Property.USE_STATIC_IMPORT_IF_POSSIBLE));
    }
  }

  private class ChooseClassAction extends DumbAwareAction {
    @Nullable
    private final Project myProject;

    protected ChooseClassAction(@Nullable Project project) {
      super((project != null && !project.isDefault() ? JavaBundle.message("action.text.choose.class.in.0", project.getName())
                                                     : JavaBundle.message("action.text.enter.class.name")));
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String fqn = getFqn();
      if (fqn != null) {
        myExpressionTypesListModel.addElement(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(fqn));
      }
    }

    private String getFqn() {
      String title = JavaBundle.message("postfix.template.editor.choose.class.title");
      if (myProject == null || myProject.isDefault()) {
        return Messages.showInputDialog(myPanel, JavaBundle.message("label.enter.fully.qualified.class.name"),
                                        JavaBundle.message("dialog.title.choose.class"), null);
      }
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(title);
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelected();
      return selectedClass != null ? selectedClass.getQualifiedName() : null;
    }
  }
}
