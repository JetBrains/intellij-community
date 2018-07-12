/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;

class JavaCodeStyleImportsPanel extends CodeStyleImportsPanelBase {
  private FullyQualifiedNamesInJavadocOptionProvider myFqnInJavadocOption;
  private SortedListModel<String> doNotInsertInnerListModel;

  @Override
  protected void fillCustomOptions(OptionGroup group) {
    myFqnInJavadocOption = new FullyQualifiedNamesInJavadocOptionProvider();

    addDoNotImportInnerListControl(group);

    group.add(myFqnInJavadocOption.getPanel());
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    applyLayoutSettings(javaSettings);
    myFqnInJavadocOption.apply(settings);
    javaSettings.setDoNotImportInner(new HashSet<>(doNotInsertInnerListModel.getItems()));
  }

  @Override
  public void reset(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    resetLayoutSettings(javaSettings);
    myFqnInJavadocOption.reset(settings);
    doNotInsertInnerListModel.addAll(javaSettings.getDoNotImportInner());
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final JavaCodeStyleSettings javaSettings = getJavaSettings(settings);
    boolean isModified = isModifiedLayoutSettings(javaSettings);
    isModified |= myFqnInJavadocOption.isModified(settings);
    isModified |= !javaSettings.getDoNotImportInner().equals(new HashSet<>(doNotInsertInnerListModel.getItems()));
    return isModified;
  }

  private static JavaCodeStyleSettings getJavaSettings(@NotNull CodeStyleSettings settings) {
    return settings.getCustomSettings(JavaCodeStyleSettings.class);
  }


  public void addDoNotImportInnerListControl(OptionGroup group) {
    doNotInsertInnerListModel = new SortedListModel<>(String::compareTo);
    final JList<String> list = new JBList<>(doNotInsertInnerListModel);

    list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    final ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(list)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(list));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
            .createWithInnerClassesScopeChooser(ApplicationBundle.message("do.not.import.inner.classes.picker.title"),
                                                GlobalSearchScope.allScope(project), new ClassFilter() {
                @Override
                public boolean isAccepted(PsiClass aClass) {
                  return aClass.getContainingClass() != null && !doNotInsertInnerListModel.getItems().contains(aClass.getName());
                }
              }, null);
          chooser.showDialog();
          final String selected = chooser.getSelected().getName();
          if (selected != null && !doNotInsertInnerListModel.getItems().contains(selected)) {
            doNotInsertInnerListModel.add(selected);
          }
        }
      }).setAddActionName(ApplicationBundle.message("do.not.import.inner.classes.add.button"))
      .setRemoveActionName(ApplicationBundle.message("do.not.import.inner.classes.remove.button")).disableUpDownActions();


    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(ApplicationBundle.message("do.not.import.inner.classes.for"), null), BorderLayout.NORTH);
    panel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);

    group.add(panel);
  }
}