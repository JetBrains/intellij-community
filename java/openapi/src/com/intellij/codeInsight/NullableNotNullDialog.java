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
package com.intellij.codeInsight;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 1/25/11
 */
public class NullableNotNullDialog extends DialogWrapper {
  private final Project myProject;
  private AnnotationsPanel myNullablePanel;
  private AnnotationsPanel myNotNullPanel;

  public NullableNotNullDialog(@NotNull Project project) {
    super(project, true);
    myProject = project;
    init();
    setTitle("Nullable/NotNull configuration");
  }

  @Override
  protected JComponent createCenterPanel() {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    final Splitter splitter = new Splitter(true);
    myNullablePanel =
      new AnnotationsPanel("Nullable", manager.getDefaultNullable(), manager.getNullables(), NullableNotNullManager.DEFAULT_NULLABLES);
    splitter.setFirstComponent(myNullablePanel.getComponent());
    myNotNullPanel =
      new AnnotationsPanel("NotNull", manager.getDefaultNotNull(), manager.getNotNulls(), NullableNotNullManager.DEFAULT_NOT_NULLS);
    splitter.setSecondComponent(myNotNullPanel.getComponent());
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setPreferredSize(JBUI.size(300, 400));
    return splitter;
  }

  @Override
  protected void doOKAction() {
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);

    manager.setNotNulls(myNotNullPanel.getAnnotations());
    manager.setDefaultNotNull(myNotNullPanel.getDefaultAnnotation());

    manager.setNullables(myNullablePanel.getAnnotations());
    manager.setDefaultNullable(myNullablePanel.getDefaultAnnotation());

    super.doOKAction();
  }

  private class AnnotationsPanel {
    private String myDefaultAnnotation;
    private final Set<String> myDefaultAnnotations;
    private final JBList myList;
    private final JPanel myComponent;

    private AnnotationsPanel(final String name, final String defaultAnnotation,
                             final Collection<String> annotations, final String[] defaultAnnotations) {
      myDefaultAnnotation = defaultAnnotation;
      myDefaultAnnotations = new HashSet(Arrays.asList(defaultAnnotations));
      myList = new JBList(annotations);
      myList.setCellRenderer(new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (value.equals(myDefaultAnnotation)) {
            setIcon(AllIcons.Diff.CurrentLine);
          } else {
            setIcon(EmptyIcon.ICON_16);
          }
          //if (myDefaultAnnotations.contains(value)) {
          //  append(" (built in)", SimpleTextAttributes.GRAY_ATTRIBUTES);
          //}
        }
      });

      final AnActionButton selectButton =
        new AnActionButton("Select annotation used for code generation", AllIcons.Actions.Checked) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            final String selectedValue = (String)myList.getSelectedValue();
            if (selectedValue == null) return;
            myDefaultAnnotation = selectedValue;
            final DefaultListModel model = (DefaultListModel)myList.getModel();

            // to show the new default value in the ui
            model.setElementAt(myList.getSelectedValue(), myList.getSelectedIndex());
          }

          @Override
          public void updateButton(AnActionEvent e) {
            final String selectedValue = (String)myList.getSelectedValue();
            final boolean enabled = selectedValue != null && !selectedValue.equals(myDefaultAnnotation);
            if (!enabled) {
              e.getPresentation().setEnabled(enabled);
            }
          }
        };

      final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myList).disableUpDownActions()
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            chooseAnnotation(name, myList);
          }
        })
        .setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton anActionButton) {
            final String selectedValue = (String)myList.getSelectedValue();
            if (selectedValue == null) return;
            if (myDefaultAnnotation.equals(selectedValue)) myDefaultAnnotation = (String)myList.getModel().getElementAt(0);

            ((DefaultListModel)myList.getModel()).removeElement(selectedValue);
          }
        })
        .addExtraAction(selectButton);
      final JPanel panel = toolbarDecorator.createPanel();
      myComponent = new JPanel(new BorderLayout());
      myComponent.setBorder(IdeBorderFactory.createTitledBorder(name + " annotations", false, new Insets(10, 0, 0, 0)));
      myComponent.add(panel);
      final AnActionButton removeButton = ToolbarDecorator.findRemoveButton(myComponent);
      myList.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          final String selectedValue = (String)myList.getSelectedValue();
          if (myDefaultAnnotations.contains(selectedValue)) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                removeButton.setEnabled(false);
              }
            });
          }
        }
      });
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setSelectedValue(myDefaultAnnotation, true);
    }

    private void chooseAnnotation(String title, JBList list) {
      final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
        .createNoInnerClassesScopeChooser("Choose " + title + " annotation", GlobalSearchScope.allScope(myProject), new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return aClass.isAnnotationType();
          }
        }, null);
      chooser.showDialog();
      final PsiClass selected = chooser.getSelected();
      if (selected == null) {
        return;
      }
      final String qualifiedName = selected.getQualifiedName();
      final DefaultListModel model = (DefaultListModel)list.getModel();
      final int index = model.indexOf(qualifiedName);
      if (index < 0) {
        model.addElement(qualifiedName);
      } else {
        myList.setSelectedIndex(index);
      }
    }

    public JComponent getComponent() {
      return myComponent;
    }

    public String getDefaultAnnotation() {
      return myDefaultAnnotation;
    }

    public String[] getAnnotations() {
      final ListModel model = myList.getModel();
      final int size = model.getSize();
      final String[] result = new String[size];
      for (int i = 0; i < size; i++) {
        result[i] = (String)model.getElementAt(i);
      }
      return result;
    }
  }
}
