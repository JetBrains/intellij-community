// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.util;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Gregory.Shrago
 */
public final class SpecialAnnotationsUtil {
  public static JPanel createSpecialAnnotationsListControl(final List<String> list, final @NlsContexts.Label String borderTitle) {
    return createSpecialAnnotationsListControl(list, borderTitle, false);
  }

  public static JPanel createSpecialAnnotationsListControl(final List<String> list,
                                                           final @NlsContexts.Label String borderTitle,
                                                           final boolean acceptPatterns) {
    return createSpecialAnnotationsListControl(list, borderTitle, acceptPatterns, aClass -> aClass.isAnnotationType());
  }

  public static JPanel createSpecialAnnotationsListControl(final List<String> list,
                                                           final @NlsContexts.Label String borderTitle,
                                                           final boolean acceptPatterns,
                                                           final Predicate<? super PsiClass> isApplicable) {
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
    return createSpecialAnnotationsListControl(borderTitle, acceptPatterns, listModel, isApplicable);
  }

  public static JPanel createSpecialAnnotationsListControl(final @NlsContexts.Label String borderTitle,
                                                           final boolean acceptPatterns,
                                                           final SortedListModel<String> listModel,
                                                           final Predicate<? super PsiClass> isApplicable) {
    final JList<String> injectionList = new JBList<>(listModel);
    injectionList.setBorder(JBUI.Borders.empty());

    injectionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    ToolbarDecorator toolbarDecorator = ToolbarDecorator
      .createDecorator(injectionList)
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
                return isApplicable.test(aClass);
              }
            }, null);
          chooser.showDialog();
          final PsiClass selected = chooser.getSelected();
          if (selected != null) {
            listModel.add(selected.getQualifiedName());
          }
        }
      })
      .setAddActionName(JavaBundle.message("special.annotations.list.add.annotation.class"))
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.LEFT);

    if (acceptPatterns) {
      toolbarDecorator
        .setAddIcon(IconUtil.getAddClassIcon())
        .addExtraAction(
          new AnActionButton(JavaBundle.message("special.annotations.list.annotation.pattern"), IconUtil.getAddPatternIcon()) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              String selectedPattern = Messages.showInputDialog(JavaBundle.message("special.annotations.list.annotation.pattern.message"),
                                                                JavaBundle.message("special.annotations.list.annotation.pattern"),
                                                                Messages.getQuestionIcon());
              if (selectedPattern != null) {
                listModel.add(selectedPattern);
              }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
              return ActionUpdateThread.EDT;
            }
          }).setButtonComparator(JavaBundle.message("special.annotations.list.add.annotation.class"),
                                 JavaBundle.message("special.annotations.list.annotation.pattern"),
                                 JavaBundle.message("special.annotations.list.remove.pattern"));
    }
    final var panel = toolbarDecorator.createPanel();
    final Dimension minimumSize = acceptPatterns ? InspectionOptionsPanel.getMinimumLongListSize() : InspectionOptionsPanel.getMinimumListSize();
    panel.setMinimumSize(minimumSize);
    panel.setPreferredSize(minimumSize);

    if (borderTitle == null) return panel;

    return UI.PanelFactory
      .panel(panel)
      .withLabel(borderTitle)
      .moveLabelOnTop()
      .resizeY(true)
      .createPanel();
  }

  public static IntentionAction createAddToSpecialAnnotationsListIntentionAction(final @IntentionName String text,
                                                                                 final @IntentionFamilyName String family,
                                                                                 final @Nls String listTitle,
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

      @Override
      public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        List<@NlsSafe String> prefixes = StreamEx.of(targetList).append(qualifiedName).sorted().toList();
        return IntentionPreviewInfo.addListOption(prefixes, qualifiedName, listTitle);
      }
    };
  }
}
