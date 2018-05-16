// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class PostfixTemplateEditorBase<Condition extends PostfixTemplateExpressionCondition> implements PostfixTemplateEditor {

  @NotNull protected final PostfixTemplateProvider myProvider;
  @NotNull protected final Editor myTemplateEditor;
  @NotNull protected final JBList<Condition> myExpressionTypesList;
  @NotNull protected final DefaultListModel<Condition> myExpressionTypesListModel;

  @NotNull protected final JPanel myTemplateEditorPanel;
  @NotNull protected final JPanel myExpressionTypesPanel;
  @NotNull protected final JBCheckBox myApplyToTheTopmostJBCheckBox;
  @NotNull protected final JPanel myEditTemplateAndConditionsPanel;
  @NotNull protected final JBLabel myExpressionVariableHint;

  protected class AddConditionAction extends DumbAwareAction {
    @NotNull
    private final Condition myCondition;

    public AddConditionAction(Condition condition) {
      super(condition.getPresentableName());
      myCondition = condition;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myExpressionTypesListModel.addElement(myCondition);
    }
  }

  public PostfixTemplateEditorBase(@NotNull PostfixTemplateProvider provider, boolean showExpressionTypes) {
    this(provider, createSimpleEditor(), showExpressionTypes);
  }
  

  public PostfixTemplateEditorBase(@NotNull PostfixTemplateProvider provider,
                                   @NotNull Editor templateEditor,
                                   boolean showExpressionTypes) {
    myProvider = provider;
    myTemplateEditor = templateEditor;

    myApplyToTheTopmostJBCheckBox = new JBCheckBox("Apply to the &topmost expression");
    DialogUtil.registerMnemonic(myApplyToTheTopmostJBCheckBox, '&');
    myTemplateEditorPanel = new JPanel(new BorderLayout());
    myTemplateEditorPanel.add(myTemplateEditor.getComponent());

    myExpressionVariableHint = new JBLabel("Use $EXPR$ variable to refer target expression");
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myExpressionVariableHint);
    myExpressionVariableHint.setFontColor(UIUtil.FontColor.BRIGHTER);

    myExpressionTypesListModel = JBList.createDefaultListModel();
    myExpressionTypesList = new JBList<>(myExpressionTypesListModel);
    myExpressionTypesList.setCellRenderer(new ColoredListCellRenderer<PostfixTemplateExpressionCondition>() {

      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends PostfixTemplateExpressionCondition> list,
                                           PostfixTemplateExpressionCondition value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        append(value.getPresentableName());
      }
    });

    myExpressionTypesPanel = new JPanel(new BorderLayout());
    FormBuilder builder = FormBuilder.createFormBuilder();
    if (showExpressionTypes) {
      myExpressionTypesPanel.add(ToolbarDecorator.createDecorator(myExpressionTypesList)
                                                 .setAddAction(button -> showAddExpressionTypePopup(button))
                                                 .setRemoveAction(button -> ListUtil.removeSelectedItems(myExpressionTypesList))
                                                 .disableUpDownActions()
                                                 .createPanel());
      myExpressionTypesPanel.setMinimumSize(new Dimension(-1, 100));
      builder.addLabeledComponent("Applicable expression types:", myExpressionTypesPanel, true);
    }


    builder.addComponent(myApplyToTheTopmostJBCheckBox);
    builder.addComponent(myTemplateEditorPanel);
    builder.addComponent(myExpressionVariableHint);

    myEditTemplateAndConditionsPanel = builder.getPanel();
  }

  @NotNull
  protected static Editor createEditor(@NotNull Project project, @NotNull Document document) {
    return TemplateEditorUtil.createEditor(false, document, project);
  }

  @NotNull
  private static Editor createSimpleEditor() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    return createEditor(defaultProject, EditorFactory.getInstance().createDocument(""));
  }

  protected final void showAddExpressionTypePopup(@NotNull AnActionButton button) {
    DefaultActionGroup group = new DefaultActionGroup();
    fillConditions(group);
    DataContext context = DataManager.getInstance().getDataContext(button.getContextComponent());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, context,
                                                                          JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    popup.show(ObjectUtils.assertNotNull(button.getPreferredPopupPoint()));
  }

  protected abstract void fillConditions(@NotNull DefaultActionGroup group);

  public void setTemplate(@Nullable PostfixTemplate rawTemplate) {
    if (!(rawTemplate instanceof EditablePostfixTemplateWithMultipleExpressions)) return;

    //noinspection unchecked
    EditablePostfixTemplateWithMultipleExpressions<Condition> template = (EditablePostfixTemplateWithMultipleExpressions)rawTemplate;

    myExpressionTypesListModel.clear();
    for (Condition condition : template.getExpressionConditions()) {
      myExpressionTypesListModel.addElement(condition);
    }
    myApplyToTheTopmostJBCheckBox.setSelected(template.isUseTopmostExpression());
    ApplicationManager.getApplication()
                      .runWriteAction(() -> myTemplateEditor.getDocument().setText(template.getLiveTemplate().getString()));
  }

  @Override
  public String getHelpId() {
    return "reference.custom.postfix.templates";
  }

  @Override
  public void dispose() {
    TemplateEditorUtil.disposeTemplateEditor(myTemplateEditor);
  }
}
