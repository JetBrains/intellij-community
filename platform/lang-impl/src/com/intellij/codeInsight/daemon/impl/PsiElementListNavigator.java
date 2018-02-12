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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.find.FindUtil;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.usages.UsageView;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.util.List;

public class PsiElementListNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PsiElementListNavigator");

  private PsiElementListNavigator() {
  }

  public static void openTargets(MouseEvent e, NavigatablePsiElement[] targets, String title, final String findUsagesTitle, ListCellRenderer listRenderer) {
    openTargets(e, targets, title, findUsagesTitle, listRenderer, null);
  }

  public static void openTargets(MouseEvent e,
                                 NavigatablePsiElement[] targets,
                                 String title,
                                 final String findUsagesTitle,
                                 ListCellRenderer listRenderer,
                                 @Nullable ListBackgroundUpdaterTask listUpdaterTask) {
    JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask);
    if (popup != null) {
      RelativePoint point = new RelativePoint(e);
      if (listUpdaterTask != null) {
        runActionAndListUpdaterTask(popup, () -> popup.show(point), listUpdaterTask);
      }
      else {
        popup.show(point);
      }
    }
  }

  public static void openTargets(Editor e, NavigatablePsiElement[] targets, String title, final String findUsagesTitle, ListCellRenderer listRenderer) {
    openTargets(e, targets, title, findUsagesTitle, listRenderer, null);
  }

  public static void openTargets(Editor e, NavigatablePsiElement[] targets, String title, final String findUsagesTitle,
                                 ListCellRenderer listRenderer, @Nullable ListBackgroundUpdaterTask listUpdaterTask) {
    final JBPopup popup = navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask);
    if (popup != null) {
      if (listUpdaterTask != null) {
        runActionAndListUpdaterTask(popup, () -> popup.showInBestPositionFor(e), listUpdaterTask);
      }
      else {
        popup.showInBestPositionFor(e);
      }
    }
  }

  /**
   * @see #navigateOrCreatePopup(NavigatablePsiElement[], String, String, ListCellRenderer, ListBackgroundUpdaterTask, Consumer)
   */
  private static void runActionAndListUpdaterTask(@NotNull Disposable popup, @NotNull Runnable action,
                                                  @NotNull ListBackgroundUpdaterTask listUpdaterTask) {
    Alarm alarm = new Alarm(popup);
    alarm.addRequest(action, 300);
    ProgressManager.getInstance().run(listUpdaterTask);
  }

  @Nullable
  private static JBPopup navigateOrCreatePopup(final NavigatablePsiElement[] targets,
                                               final String title,
                                               final String findUsagesTitle,
                                               final ListCellRenderer listRenderer,
                                               @Nullable final ListBackgroundUpdaterTask listUpdaterTask) {
    return navigateOrCreatePopup(targets, title, findUsagesTitle, listRenderer, listUpdaterTask, selectedElements -> {
      for (Object element : selectedElements) {
        PsiElement selected = (PsiElement)element;
        if (selected.isValid()) {
          ((NavigatablePsiElement)selected).navigate(true);
        }
      }
    });
  }

  /**
   * listUpdaterTask should be started after alarm is initialized so one-item popup won't blink
   */
  @Nullable
  public static JBPopup navigateOrCreatePopup(@NotNull final NavigatablePsiElement[] targets,
                                              final String title,
                                              final String findUsagesTitle,
                                              final ListCellRenderer listRenderer,
                                              @Nullable final ListBackgroundUpdaterTask listUpdaterTask,
                                              @NotNull final Consumer<Object[]> consumer) {
    if (targets.length == 0) return null;
    if (targets.length == 1 && (listUpdaterTask == null || listUpdaterTask.isFinished())) {
      consumer.consume(targets);
      return null;
    }
    final CollectionListModel<NavigatablePsiElement> model = new CollectionListModel<>(targets);
    final JBList<NavigatablePsiElement> list = new JBList<>(model);
    HintUpdateSupply.installSimpleHintUpdateSupply(list);

    list.setTransferHandler(new TransferHandler(){
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        final Object[] selectedValues = list.getSelectedValues();
        final PsiElement[] copy = new PsiElement[selectedValues.length];
        for (int i = 0; i < selectedValues.length; i++) {
          copy[i] = (PsiElement)selectedValues[i];
        }
        return new PsiCopyPasteManager.MyTransferable(copy);
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

    list.setCellRenderer(listRenderer);
    list.setFont(EditorUtil.getEditorFont());

    final IPopupChooserBuilder<NavigatablePsiElement> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(list);
    if (listRenderer instanceof PsiElementListCellRenderer) {
      ((PsiElementListCellRenderer)listRenderer).installSpeedSearch(builder);
    }

    IPopupChooserBuilder<NavigatablePsiElement> popupChooserBuilder = builder.
      setTitle(title).
      setMovable(true).
      setResizable(true).
      setItemsChoosenCallback(selectedValues -> {
        consumer.consume(ArrayUtil.toObjectArray(selectedValues));
      }).
      setCancelCallback(() -> {
        HintUpdateSupply.hideHint(list);
        if (listUpdaterTask != null) {
          listUpdaterTask.cancelTask();
        }
        return true;
      });
    final Ref<UsageView> usageView = new Ref<>();
    if (findUsagesTitle != null) {
      popupChooserBuilder = popupChooserBuilder.setCouldPin(popup -> {
        final List<NavigatablePsiElement> items = model.getItems();
        usageView.set(FindUtil.showInUsageView(null, items.toArray(PsiElement.EMPTY_ARRAY), findUsagesTitle, targets[0].getProject()));
        popup.cancel();
        return false;
      });
    }

    final JBPopup popup = popupChooserBuilder.createPopup();

    builder.getScrollPane().setBorder(null);
    builder.getScrollPane().setViewportBorder(null);

    if (listUpdaterTask != null) {
      listUpdaterTask.init(popup, list, usageView);
    }
    return popup;
  }
}
