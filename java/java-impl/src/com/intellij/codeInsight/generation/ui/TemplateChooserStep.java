// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManagerBase;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.template.TemplateResource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public abstract class TemplateChooserStep extends StepAdapter {
  private final JComponent myPanel;
  private final ComboBox<String> myComboBox;
  private final EqualsHashCodeTemplatesManagerBase myTemplatesManager;
  private final boolean myShowEqualsOptions;
  private @Nullable Set<String> myInvalidTemplates = null;

  protected TemplateChooserStep(PsiElement contextElement, EqualsHashCodeTemplatesManagerBase templatesManager) {
    this(contextElement, templatesManager, true);
  }

  protected TemplateChooserStep(PsiElement contextElement, EqualsHashCodeTemplatesManagerBase templatesManager, boolean showEqualsOptions) {
    myShowEqualsOptions = showEqualsOptions;
    myPanel = new JPanel(new VerticalFlowLayout());
    final JPanel templateChooserPanel = new JPanel(new BorderLayout());
    final JLabel templateChooserLabel = new JLabel(JavaBundle.message("generate.equals.hashcode.template"));
    templateChooserPanel.add(templateChooserLabel, BorderLayout.WEST);

    myTemplatesManager = templatesManager;
    Collection<TemplateResource> templates = myTemplatesManager.getAllTemplates();
    myComboBox = new ComboBox<>(templates.stream()
                                  .map(EqualsHashCodeTemplatesManagerBase::getTemplateBaseName)
                                  .distinct()
                                  .toArray(String[]::new));
    myComboBox.setSelectedItem(myTemplatesManager.getDefaultTemplateBaseName());
    myComboBox.setSwingPopup(false);
    Project project = contextElement.getProject();
    final ComponentWithBrowseButton<ComboBox<?>> comboBoxWithBrowseButton =
      new ComponentWithBrowseButton<>(myComboBox, e -> {
        EqualsHashCodeTemplatesPanel ui = createTemplatesPanel(project);
        ShowSettingsUtil.getInstance().editConfigurable(myPanel, ui);
        String[] names = myTemplatesManager.getAllTemplates().stream()
          .map(EqualsHashCodeTemplatesManagerBase::getTemplateBaseName)
          .distinct()
          .toArray(String[]::new);
        myComboBox.setModel(new DefaultComboBoxModel<>(names));
        myComboBox.setSelectedItem(myTemplatesManager.getDefaultTemplateBaseName());
      });
    templateChooserLabel.setLabelFor(myComboBox);
    ReadAction.nonBlocking(() -> {
        GlobalSearchScope resolveScope = contextElement.getResolveScope();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        Set<String> names = new LinkedHashSet<>();
        Set<String> invalid = new HashSet<>();

        DumbService dumbService = DumbService.getInstance(project);
        for (TemplateResource resource : myTemplatesManager.getAllTemplates()) {
          String templateBaseName = EqualsHashCodeTemplatesManagerBase.getTemplateBaseName(resource);
          if (names.add(templateBaseName)) {
            String className = resource.getClassName();
            if (className != null &&
                dumbService.computeWithAlternativeResolveEnabled(() -> psiFacade.findClass(className, resolveScope) == null)) {
              invalid.add(templateBaseName);
            }
          }
        }
        return invalid;
      })
      .expireWhen(() -> isDisposed())
      .finishOnUiThread(ModalityState.any(), invalid -> {
        myInvalidTemplates = invalid;
        updateErrorMessage();
        myComboBox.repaint();
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    myComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(value); //NON-NLS
      if (myInvalidTemplates != null && myInvalidTemplates.contains(value)) {
        label.setForeground(JBColor.RED);
      }
    }));
    myComboBox.addActionListener(e -> updateErrorMessage());

    templateChooserPanel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
    myPanel.add(templateChooserPanel);

    appendAdditionalOptions(myPanel);
  }

  private @NotNull EqualsHashCodeTemplatesPanel createTemplatesPanel(Project project) {
    EqualsHashCodeTemplatesPanel ui = new EqualsHashCodeTemplatesPanel(project, myTemplatesManager) {
      @Override
      protected @NotNull Map<String, PsiType> getEqualsImplicitVars() {
        return myTemplatesManager.getEqualsImplicitVars(project);
      }

      @Override
      protected @NotNull Map<String, PsiType> getHashCodeImplicitVars() {
        return myTemplatesManager.getHashCodeImplicitVars(project);
      }
    };
    ui.selectNodeInTree(myTemplatesManager.getDefaultTemplateBaseName());
    return ui;
  }

  protected void appendAdditionalOptions(JComponent stepPanel) {
    boolean useInstanceof = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    if (myShowEqualsOptions) {
      JLabel label = new JLabel(JavaBundle.message("generate.equals.hashcode.type.comparison.label"));
      label.setBorder(JBUI.Borders.emptyTop(UIUtil.LARGE_VGAP));
      panel.add(label);
      ContextHelpLabel contextHelp = ContextHelpLabel.create(JavaBundle.message("generate.equals.hashcode.comparison.table"));
      contextHelp.setBorder(JBUI.Borders.empty(UIUtil.LARGE_VGAP, 2, 0, 0));
      panel.add(contextHelp);
      JRadioButton instanceofButton =
        new JRadioButton(JavaBundle.message("generate.equals.hashcode.instanceof.type.comparison"), useInstanceof);
      instanceofButton.setBorder(JBUI.Borders.emptyLeft(16));
      JRadioButton getClassButton =
        new JRadioButton(JavaBundle.message("generate.equals.hashcode.getclass.type.comparison"), !useInstanceof);
      getClassButton.setBorder(JBUI.Borders.emptyLeft(16));
      ButtonGroup group = new ButtonGroup();
      group.add(instanceofButton);
      group.add(getClassButton);
      instanceofButton.addActionListener(e -> CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = true);
      getClassButton.addActionListener(e -> CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = false);
      stepPanel.add(panel);
      stepPanel.add(instanceofButton);
      stepPanel.add(getClassButton);
    }

    final JCheckBox gettersCheckbox = createUseGettersInsteadOfFieldsCheckbox();
    if (gettersCheckbox != null) {
      stepPanel.add(gettersCheckbox);
    }
  }

  protected @Nullable JCheckBox createUseGettersInsteadOfFieldsCheckbox() {
    final JCheckBox gettersCheckbox = new NonFocusableCheckBox(JavaBundle.message("generate.equals.hashcode.use.getters"));
    gettersCheckbox.setBorder(JBUI.Borders.emptyTop(UIUtil.LARGE_VGAP));
    gettersCheckbox.setSelected(CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE);
    gettersCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        CodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE = gettersCheckbox.isSelected();
      }
    });
    return gettersCheckbox;
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    myTemplatesManager.setDefaultTemplate((String)myComboBox.getSelectedItem());
    super._commit(finishChosen);
  }

  private void updateErrorMessage() {
    String item = (String)myComboBox.getSelectedItem();
    if (myInvalidTemplates != null && myInvalidTemplates.contains(item)) {
      TemplateResource template =
        myTemplatesManager.findTemplateByName(EqualsHashCodeTemplatesManagerBase.toEqualsName(item));
      if (template != null) {
        String className = template.getClassName();
        setErrorText(className != null ? JavaBundle.message("dialog.message.class.not.found", className)
                                                : JavaBundle.message("dialog.message.template.not.applicable"), myComboBox);
      }
      else {
        setErrorText(JavaBundle.message("dialog.message.template.not.found"), myComboBox);
      }
    }
    else {
      setErrorText(null, myComboBox);

    }
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  protected abstract void setErrorText(@NlsContexts.DialogMessage @Nullable String errorText, JComponent component);
  protected abstract boolean isDisposed();
}
