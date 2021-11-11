// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.ide.structureView.impl.StructureNodeRenderer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MethodListDlg extends DialogWrapper {

  private static final Comparator<PsiMethod> METHOD_NAME_COMPARATOR = Comparator.comparing(PsiMethod::getName, String::compareToIgnoreCase);

  private final SortedListModel<PsiMethod> myListModel = new SortedListModel<>(METHOD_NAME_COMPARATOR);
  private final JList<PsiMethod> myList = new JBList<>(myListModel);
  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private final boolean myCreateMethodListSuccess;

  public MethodListDlg(@NotNull PsiClass psiClass, @NotNull Condition<? super PsiMethod> filter, @NotNull JComponent parent) {
    super(parent, false);
    myCreateMethodListSuccess = createList(psiClass.getAllMethods(), filter);
    myWholePanel.add(ScrollPaneFactory.createScrollPane(myList));
    myList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull final JList<? extends PsiMethod> list,
                                           @NotNull final PsiMethod psiMethod,
                                           final int index,
                                           final boolean selected,
                                           final boolean hasFocus) {
        append(PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, 0),
               StructureNodeRenderer.applyDeprecation(psiMethod, SimpleTextAttributes.REGULAR_ATTRIBUTES));
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
          return;
        }
        if (!psiClass.equals(containingClass)) {
          append(" (" + containingClass.getQualifiedName() + ")",
                 StructureNodeRenderer.applyDeprecation(containingClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
        }
      }
    });
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        MethodListDlg.this.close(OK_EXIT_CODE);
        return true;
      }
    }.installOn(myList);

    ScrollingUtil.ensureSelectionExists(myList);
    TreeUIHelper.getInstance().installListSpeedSearch(myList);
    setTitle(ExecutionBundle.message("choose.test.method.dialog.title"));
    init();
  }

  /**
   * @return false if the progress dialog was cancelled by user, true otherwise
   */
  private boolean createList(final PsiMethod@NotNull [] allMethods, final Condition<? super PsiMethod> filter) {
    if (allMethods.length == 0) return true;

    final BuildMethodListTask task = new BuildMethodListTask(allMethods, filter, myListModel);
    ProgressManager.getInstance().run(task);

    return !task.isCancelled();
  }

  @Override
  public void show() {
    if (!myCreateMethodListSuccess) return;
    super.show();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myWholePanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public PsiMethod getSelected() {
    return myList.getSelectedValue();
  }

  private final static class BuildMethodListTask extends Task.Modal {
    private final List<SmartPsiElementPointer<PsiMethod>> myList = new ArrayList<>();
    private final PsiMethod@NotNull [] myAllMethods;
    private final Condition<? super PsiMethod> myFilter;
    private final SortedListModel<PsiMethod> myListModel;
    private boolean cancelled = false;

    private BuildMethodListTask(final PsiMethod@NotNull [] allMethods,
                                final Condition<? super PsiMethod> filter,
                                SortedListModel<PsiMethod> listModel) {
      super(allMethods[0].getProject(), ExecutionBundle.message("browse.method.dialog.looking.for.methods"), true);
      myAllMethods = allMethods;
      myFilter = filter;
      myListModel = listModel;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      ReadAction.nonBlocking(this::filterMethods).executeSynchronously();
    }

    private void filterMethods() {
      final List<SmartPsiElementPointer<PsiMethod>> methodPointers = ContainerUtil.map(myAllMethods, SmartPointerManager::createPointer);
      for (SmartPsiElementPointer<PsiMethod> methodPointer : methodPointers) {
        if (myFilter.value(methodPointer.getElement())) {
          myList.add(methodPointer);
        }
      }
    }

    @Override
    public void onSuccess() {
      for (SmartPsiElementPointer<PsiMethod> e : myList) {
        final PsiMethod dereference = e.dereference();
        if (dereference == null) continue;
        myListModel.add(dereference);
      }
    }

    @Override
    public void onCancel() {
      cancelled = true;
    }

    private boolean isCancelled() {
      return cancelled;
    }
  }
}
