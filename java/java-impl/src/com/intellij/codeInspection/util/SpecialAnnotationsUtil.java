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

package com.intellij.codeInspection.util;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class SpecialAnnotationsUtil {
  public static JPanel createSpecialAnnotationsListControl(final List<String> list, final String borderTitle) {
    return createSpecialAnnotationsListControl(list, borderTitle, false);
  }

  public static JPanel createSpecialAnnotationsListControl(final List<String> list,
                                                           final String borderTitle,
                                                           final boolean acceptPatterns) {
    return createSpecialAnnotationsListControl(list, borderTitle, acceptPatterns, aClass -> aClass.isAnnotationType());
  }

  public static JPanel createSpecialAnnotationsListControl(final List<String> list,
                                                           final String borderTitle,
                                                           final boolean acceptPatterns,
                                                           final Condition<? super PsiClass> isApplicable) {
    @SuppressWarnings("Convert2Diamond")
    SortedListModel<String> listModel = new SortedListModel<String>(Comparator.naturalOrder());
    for (String s : list) {
      listModel.add(s);
    }
    listModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        listChanged();
      }

      private void listChanged() {
        list.clear();
        for (int i = 0; i < listModel.getSize(); i++) {
          list.add(listModel.getElementAt(i));
        }
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        listChanged();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        listChanged();
      }
    });
    return createSpecialAnnotationsListControl(borderTitle, acceptPatterns, isApplicable, listModel);
  }

  public static JPanel createSpecialAnnotationsListControl(final String borderTitle,
                                                           final boolean acceptPatterns,
                                                           final Condition<? super PsiClass> isApplicable,
                                                           final SortedListModel<? super String> listModel) {
    final JList injectionList = new JBList(listModel);

    injectionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(injectionList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(injectionList));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
            .createWithInnerClassesScopeChooser(JavaBundle.message("special.annotations.list.annotation.class"),
                                                GlobalSearchScope.allScope(project), new ClassFilter() {
              @Override
              public boolean isAccepted(PsiClass aClass) {
                return isApplicable.value(aClass);
              }
            }, null);
          chooser.showDialog();
          final PsiClass selected = chooser.getSelected();
          if (selected != null) {
            listModel.add(selected.getQualifiedName());
          }
        }
      }).setAddActionName(JavaBundle.message("special.annotations.list.add.annotation.class")).disableUpDownActions();

    if (acceptPatterns) {
      toolbarDecorator
        .setAddIcon(IconUtil.getAddClassIcon())
        .addExtraAction(
          new AnActionButton(JavaBundle.message("special.annotations.list.annotation.pattern"), IconUtil.getAddPatternIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              String selectedPattern = Messages.showInputDialog(JavaBundle.message("special.annotations.list.annotation.pattern"),
                                                                JavaBundle.message("special.annotations.list.annotation.pattern"),
                                                                Messages.getQuestionIcon());
              if (selectedPattern != null) {
                listModel.add(selectedPattern);
              }
            }
          }).setButtonComparator(JavaBundle.message("special.annotations.list.add.annotation.class"),
                                 JavaBundle.message("special.annotations.list.annotation.pattern"), "Remove");
    }

    if (borderTitle == null) {
      return toolbarDecorator.createPanel();
    }
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(borderTitle, null), BorderLayout.NORTH);
    panel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    return panel;
  }

  public static IntentionAction createAddToSpecialAnnotationsListIntentionAction(final String text,
                                                                                 final String family,
                                                                                 final List<String> targetList,
                                                                                 final String qualifiedName) {
    return new IntentionAction() {
      @Override
      @NotNull
      public String getText() {
        return text;
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return family;
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        SpecialAnnotationsUtilBase.doQuickFixInternal(project, targetList, qualifiedName);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }
}
