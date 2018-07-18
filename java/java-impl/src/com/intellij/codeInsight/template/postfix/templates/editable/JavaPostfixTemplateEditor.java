// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateEditorBase;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
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
import com.intellij.psi.*;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class JavaPostfixTemplateEditor extends PostfixTemplateEditorBase<JavaPostfixTemplateExpressionCondition> {

  @NotNull private final JPanel myPanel;
  @NotNull private final ComboBox<LanguageLevel> myLanguageLevelCombo;

  public JavaPostfixTemplateEditor(@NotNull PostfixTemplateProvider provider) {
    super(provider, createEditor(), true);
    myLanguageLevelCombo = new ComboBox<>(LanguageLevel.values());
    myLanguageLevelCombo.setRenderer(new ColoredListCellRenderer<LanguageLevel>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, LanguageLevel value, int index, boolean selected, boolean hasFocus) {
        append(value.getPresentableText());
      }
    });

    myPanel = FormBuilder.createFormBuilder()
                         .addLabeledComponent("Minimum language level:", myLanguageLevelCombo)
                         .addComponent(myEditTemplateAndConditionsPanel)
                         .getPanel();
  }

  @NotNull
  private static Editor createEditor() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    return createEditor(defaultProject, createDocument(defaultProject));
  }

  @NotNull
  @Override
  public JavaEditablePostfixTemplate createTemplate(@NotNull String templateId, @NotNull String templateName) {
    LanguageLevel selectedLanguageLevel = ObjectUtils.tryCast(myLanguageLevelCombo.getSelectedItem(), LanguageLevel.class);
    LanguageLevel languageLevel = ObjectUtils.notNull(selectedLanguageLevel, LanguageLevel.JDK_1_3);
    Set<JavaPostfixTemplateExpressionCondition> conditions = ContainerUtil.newLinkedHashSet();
    ContainerUtil.addAll(conditions, myExpressionTypesListModel.elements());
    String templateText = myTemplateEditor.getDocument().getText();
    boolean useTopmostExpression = myApplyToTheTopmostJBCheckBox.isSelected();
    return new JavaEditablePostfixTemplate(templateId, templateName, templateText, "", conditions, languageLevel, useTopmostExpression,
                                           myProvider);
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
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment("", psiFacade.findPackage(""), true);
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
      myLanguageLevelCombo.setSelectedItem(((JavaEditablePostfixTemplate)template).getMinimumLanguageLevel());
    }
  }
  
  private class ChooseClassAction extends DumbAwareAction {
    @Nullable
    private final Project myProject;

    protected ChooseClassAction(@Nullable Project project) {
      super((project != null && !project.isDefault() ? "choose class in " + project.getName() + "..." : "enter class name..."));
      myProject = project;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String fqn = getFqn();
      if (fqn != null) {
        myExpressionTypesListModel.addElement(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(fqn));
      }
    }

    private String getFqn() {
      String title = "Choose Class";
      if (myProject == null || myProject.isDefault()) {
        return Messages.showInputDialog(myPanel, title, title, null);
      }
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(title);
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelected();
      return selectedClass != null ? selectedClass.getQualifiedName() : null;
    }
  }
}
