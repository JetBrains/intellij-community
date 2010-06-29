/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class SpecialAnnotationsUtil {
  public static JPanel createSpecialAnnotationsListControl(final List<String> list, final String borderTitle) {
    final SortedListModel<String> listModel = new SortedListModel<String>(new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareTo(o2);
      }
    });
    final JList injectionList = new JBList(listModel);
    for (String s : list) {
      listModel.add(s);
    }
    injectionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final ReorderableListController<String> controller = ReorderableListController.create(injectionList, actionGroup);
    controller.addAddAction(InspectionsBundle.message("special.annotations.list.add.annotation.class"), new Factory<String>() {
      public String create() {
        return Messages.showInputDialog(InspectionsBundle.message("special.annotations.list.annotation.class"),
                                        InspectionsBundle.message("special.annotations.list.add.annotation.class"),
                                        Messages.getQuestionIcon());
      }
    }, true);
    controller.addRemoveAction(InspectionsBundle.message("special.annotations.list.remove.annotation.class"));
    injectionList.getModel().addListDataListener(new ListDataListener() {
      public void intervalAdded(ListDataEvent e) {
        listChanged();
      }

      private void listChanged() {
        list.clear();
        for (int i = 0; i < listModel.getSize(); i++) {
            list.add((String)listModel.getElementAt(i));
        }
      }

      public void intervalRemoved(ListDataEvent e) {
        listChanged();
      }

      public void contentsChanged(ListDataEvent e) {
        listChanged();
      }
    });

    final JScrollPane listScrollPane = ScrollPaneFactory.createScrollPane(injectionList);
//    listScrollPane.setBorder(BorderFactory.createEtchedBorder());
    listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    final FontMetrics fontMetrics = injectionList.getFontMetrics(injectionList.getFont());
    listScrollPane.setPreferredSize(new Dimension(0, fontMetrics.getHeight() * 5));
    listScrollPane.setMinimumSize(new Dimension(0, fontMetrics.getHeight() * 3));
    //int height = injectionList.getCellRenderer().getListCellRendererComponent(injectionList, "foo", 0, false, false).getSize().height;
    //injectionList.setFixedCellHeight(height);
    //injectionList.setPreferredSize(new Dimension(0, height * 3));
    //injectionList.setMinimumSize(new Dimension(0, height * 3));
//    injectionList.setVisibleRowCount(3);

    final JPanel listPanel = new JPanel(new BorderLayout());

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    listPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    listPanel.add(listScrollPane, BorderLayout.CENTER);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(borderTitle, null), BorderLayout.NORTH);
    panel.add(listPanel, BorderLayout.CENTER);
    return panel;
  }

  public static IntentionAction createAddToSpecialAnnotationsListIntentionAction(final String text, final String family, final List<String> targetList,
                                                                                 final String qualifiedName) {
    return new IntentionAction() {
      @NotNull
      public String getText() {
        return text;
      }

      @NotNull
      public String getFamilyName() {
        return family;
      }

      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        doQuickFixInternal(project, targetList, qualifiedName);
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  public static LocalQuickFix createAddToSpecialAnnotationsListQuickFix(final String text, final String family, final List<String> targetList,
                                                                                 final String qualifiedName,
                                                                                 final PsiElement context) {
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return text;
      }

      @NotNull
      public String getFamilyName() {
        return family;
      }

      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        doQuickFixInternal(project, targetList, qualifiedName);
      }
    };
  }

  private static void doQuickFixInternal(final Project project, final List<String> targetList, final String qualifiedName) {
    targetList.add(qualifiedName);
    Collections.sort(targetList);
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    //correct save settings
    ((InspectionProfileImpl)inspectionProfile).isProperSetting(HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME));

    //TODO lesya
    InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
    /*
    try {
      inspectionProfile.save();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }

    */
  }

  public static void createAddToSpecialAnnotationFixes(final PsiModifierListOwner owner, final Processor<String> processor) {
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
      for (PsiAnnotation psiAnnotation : psiAnnotations) {
        @NonNls final String name = psiAnnotation.getQualifiedName();
        if (name == null) continue;
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.jetbrains.")) continue;
        if (!processor.process(name)) break;
      }
    }
  }

}
