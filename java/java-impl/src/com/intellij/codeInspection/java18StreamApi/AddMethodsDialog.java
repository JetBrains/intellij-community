/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18StreamApi;


import com.intellij.codeInsight.intention.impl.config.ActionUsagePanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodsDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance(AddMethodsDialog.class);
  @NotNull private final Project myProject;

  private JPanel myPanel;
  private ComboBox myTemplatesCombo;
  private ClassNameReferenceEditor myClassNameEditor;
  private ComboBox myMethodNameCombo;
  private ActionUsagePanel myBeforeActionPanel;
  private ActionUsagePanel myAfterActionPanel;
  private JPanel myExamplePanel;

  @SuppressWarnings("unchecked")
  protected AddMethodsDialog(@NotNull final Project project, @NotNull final Component parent, boolean canBeParent) {
    super(parent, canBeParent);
    myProject = project;
    myTemplatesCombo.setEnabled(false);
    myTemplatesCombo.setRenderer(new ColoredListCellRenderer<PseudoLambdaReplaceTemplate>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list,
                                           PseudoLambdaReplaceTemplate template,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (template == null) {
          return;
        }
        append("stream.");
        final String streamApiMethodName = template.getStreamApiMethodName();
        if (StreamApiConstants.STREAM_STREAM_API_METHODS.getValue().contains(streamApiMethodName)) {
          append(streamApiMethodName + "()", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          LOG.assertTrue(StreamApiConstants.FAKE_FIND_MATCHED.equals(streamApiMethodName));
          append(String.format(StreamApiConstants.FAKE_FIND_MATCHED_PATTERN, "condition"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          append(" or ");
          append(".orElseGet(() -> defaultValue)", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    });
    myTemplatesCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        final PseudoLambdaReplaceTemplate template = (PseudoLambdaReplaceTemplate)e.getItem();
        final Collection<PsiMethod> methods = (Collection<PsiMethod>)myMethodNameCombo.getSelectedItem();
        if (methods == null) {
          return;
        }
        for (PsiMethod method : methods) {
          if (template.validate(method) != null) {
            showTemplateExample(template, method);
            break;
          }
        }
      }
    });
    myMethodNameCombo.setModel(new DefaultComboBoxModel());
    myMethodNameCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (!myExamplePanel.isEnabled()) {
          myExamplePanel.setEnabled(true);
        }
        final Collection<PseudoLambdaReplaceTemplate> suitableTemplates = new LinkedHashSet<>();
        final Collection<PsiMethod> methods = (Collection<PsiMethod>) e.getItem();
        for (PseudoLambdaReplaceTemplate template : PseudoLambdaReplaceTemplate.getAllTemplates()) {
          for (PsiMethod method : methods) {
            if (template.validate(method) != null) {
              if (suitableTemplates.isEmpty()) {
                showTemplateExample(template, method);
              }
              suitableTemplates.add(template);
            }
          }
        }
        if (!myTemplatesCombo.isEnabled()) {
          myTemplatesCombo.setEnabled(true);
        }
        LOG.assertTrue(!suitableTemplates.isEmpty());
        final List<PseudoLambdaReplaceTemplate> templatesAsList = new ArrayList<>(suitableTemplates);
        myTemplatesCombo.setModel(new CollectionComboBoxModel(templatesAsList));
        myTemplatesCombo.setSelectedItem(templatesAsList.get(0));
      }
    });
    myMethodNameCombo.setRenderer(new ListCellRendererWrapper<Collection<PsiMethod>>() {
      @Override
      public void customize(JList list, Collection<PsiMethod> methods, int index, boolean selected, boolean hasFocus) {
        if (methods != null) {
          LOG.assertTrue(!methods.isEmpty());
          setText(ContainerUtil.getFirstItem(methods).getName());
        }
      }
    });
    myClassNameEditor.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        final String classFqn = e.getDocument().getText();
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(classFqn, GlobalSearchScope.allScope(project));
        final DefaultComboBoxModel comboBoxModel = (DefaultComboBoxModel)myMethodNameCombo.getModel();
        comboBoxModel.removeAllElements();
        if (aClass == null) {
          enable(false);
        }
        else {
          final List<PseudoLambdaReplaceTemplate> possibleTemplates = PseudoLambdaReplaceTemplate.getAllTemplates();
          final LinkedMultiMap<String, PsiMethod> nameToMethod = new LinkedMultiMap<>();
          for (PsiMethod m : ContainerUtil.filter(aClass.getMethods(), method -> {
            if (method.isConstructor() ||
                !method.hasModifierProperty(PsiModifier.STATIC) ||
                method.hasModifierProperty(PsiModifier.PRIVATE)) {
              return false;
            }
            boolean templateFound = false;
            for (PseudoLambdaReplaceTemplate template : possibleTemplates) {
              if (template.validate(method) != null) {
                templateFound = true;
              }
            }
            if (!templateFound) {
              return false;
            }
            return true;
          })) {
            nameToMethod.putValue(m.getName(), m);
          }
          for (Map.Entry<String, Collection<PsiMethod>> entry : nameToMethod.entrySet()) {
            comboBoxModel.addElement(entry.getValue());
          }
          final boolean isSuitableMethodsFound = comboBoxModel.getSize() != 0;
          enable(isSuitableMethodsFound);
        }
      }
    });

    setOKActionEnabled(false);
    init();
  }

  private void enable(boolean isEnabled) {
    myMethodNameCombo.setEnabled(isEnabled);
    myTemplatesCombo.setEnabled(isEnabled);
    setOKActionEnabled(isEnabled);
    myExamplePanel.setEnabled(isEnabled);
    if (!isEnabled) {
      myBeforeActionPanel.reset("", StdFileTypes.JAVA);
      myAfterActionPanel.reset("", StdFileTypes.JAVA);
    }
  }

  private void showTemplateExample(final PseudoLambdaReplaceTemplate template, final PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    LOG.assertTrue(aClass != null);
    final String fqn = aClass.getQualifiedName();
    LOG.assertTrue(fqn != null);
    final String parameters =
      StringUtil.join(ContainerUtil.map(method.getParameterList().getParameters(), parameter -> parameter.getName()), ", ");
    final String expressionText = fqn + "." + method.getName() + "(" + parameters + ")";
    final PsiExpression psiExpression = JavaPsiFacade.getElementFactory(method.getProject())
      .createExpressionFromText(expressionText, null);
    LOG.assertTrue(psiExpression instanceof PsiMethodCallExpression);
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)psiExpression;
    template.convertToStream(methodCallExpression, method, false);
    myBeforeActionPanel.reset("void example() {\n  <spot>" + methodCallExpression.getText() + "</spot>;\n}", StdFileTypes.JAVA);
    myAfterActionPanel.reset("void example() {\n  <spot>" + template.convertToStream(methodCallExpression, method, true).getText() + "</spot>\n}", StdFileTypes.JAVA);
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myBeforeActionPanel);
    Disposer.dispose(myAfterActionPanel);
    super.dispose();
  }

  private void createUIComponents() {
    myClassNameEditor = new ClassNameReferenceEditor(myProject, null);
  }

  public StaticPseudoFunctionalStyleMethodOptions.PipelineElement getSelectedElement() {
    return new StaticPseudoFunctionalStyleMethodOptions.PipelineElement(myClassNameEditor.getText(),
                                                                        ContainerUtil.getFirstItem((Collection < PsiMethod >)myMethodNameCombo.getSelectedItem()).getName(),
                                                                        (PseudoLambdaReplaceTemplate)myTemplatesCombo.getSelectedItem());
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassNameEditor;
  }
}
