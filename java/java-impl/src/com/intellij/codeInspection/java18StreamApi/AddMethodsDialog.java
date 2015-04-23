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


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.ClassNameReferenceEditor;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodsDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance(AddMethodsDialog.class);
  @NotNull private final Project myProject;

  private JPanel myPanel;
  private ComboBox myPatternsCombo;
  private ClassNameReferenceEditor myClassNameEditor;
  private ComboBox myMethodNameCombo;

  @SuppressWarnings("unchecked")
  protected AddMethodsDialog(@NotNull final Project project, @NotNull final Component parent, boolean canBeParent) {
    super(parent, canBeParent);
    myProject = project;
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myPatternsCombo.setModel(model);
    for (String methodName : StreamApiConstants.STREAM_STREAM_API_METHODS.getValue()) {
      model.addElement(methodName);
    }
    model.addElement(StreamApiConstants.FAKE_FIND_MATCHED);
    myPatternsCombo.setRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(JList list, String methodName, int index, boolean selected, boolean hasFocus) {
        append("stream.");
        if (StreamApiConstants.STREAM_STREAM_API_METHODS.getValue().contains(methodName)) {
          append(methodName + "()", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          LOG.assertTrue(StreamApiConstants.FAKE_FIND_MATCHED.equals(methodName));
          append(String.format(StreamApiConstants.FAKE_FIND_MATCHED_PATTERN, "condition"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          append(" or ");
          append(String.format(StreamApiConstants.FAKE_FIND_MATCHED_WITH_DEFAULT_PATTERN, "condition", "defaultValue"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    });
    myMethodNameCombo.setModel(new DefaultComboBoxModel());
    myClassNameEditor.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        final String classFqn = e.getDocument().getText();
        final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(classFqn, GlobalSearchScope.allScope(project));
        final DefaultComboBoxModel comboBoxModel = (DefaultComboBoxModel)myMethodNameCombo.getModel();
        comboBoxModel.removeAllElements();
        if (aClass == null) {
          myMethodNameCombo.setEnabled(false);
        }
        else {
          for (String name : ContainerUtil.newTreeSet(ContainerUtil.mapNotNull(aClass.getMethods(), new Function<PsiMethod, String>() {
            @Override
            public String fun(PsiMethod method) {
              if (method.isConstructor() ||
                  !method.hasModifierProperty(PsiModifier.STATIC) ||
                  method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return null;
              }
              return method.getName();
            }
          }))) {
            comboBoxModel.addElement(name);
          }
          myMethodNameCombo.setEnabled(true);
        }
      }
    });
    init();
  }

  private void createUIComponents() {
    myClassNameEditor = new ClassNameReferenceEditor(myProject, null);
  }

  public StaticPseudoFunctionalStyleMethodOptions.PipelineElement getSelectedElement() {
    return new StaticPseudoFunctionalStyleMethodOptions.PipelineElement(myClassNameEditor.getText(),
                                                                        (String)myMethodNameCombo.getSelectedItem(),
                                                                        (String)myPatternsCombo.getSelectedItem());
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
