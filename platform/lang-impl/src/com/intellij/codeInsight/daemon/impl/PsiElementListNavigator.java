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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBListWithHintProvider;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiElementListNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PsiElementListNavigator");

  private PsiElementListNavigator() {
  }

  public static void openTargets(MouseEvent e, NavigatablePsiElement[] targets, String title, ListCellRenderer listRenderer) {
    openTargets(e, targets, title, listRenderer, null);
  }

  public static void openTargets(MouseEvent e,
                                 NavigatablePsiElement[] targets,
                                 String title,
                                 ListCellRenderer listRenderer,
                                 @Nullable AppenderTask appenderTask) {
    JBPopup popup = navigateOrCreatePopup(targets, title, listRenderer, appenderTask);
    if (popup != null) popup.show(new RelativePoint(e));
  }

  public static void openTargets(Editor e, NavigatablePsiElement[] targets, String title, ListCellRenderer listRenderer) {
    JBPopup popup = navigateOrCreatePopup(targets, title, listRenderer, null);
    if (popup != null) popup.showInBestPositionFor(e);
  }

  @Nullable
  private static JBPopup navigateOrCreatePopup(final NavigatablePsiElement[] targets,
                                               final String title,
                                               final ListCellRenderer listRenderer,
                                               final @Nullable AppenderTask appenderTask) {
    if (targets.length == 0) return null;
    if (targets.length == 1) {
      targets[0].navigate(true);
      return null;
    }
    final JBListWithHintProvider list = new JBListWithHintProvider(new CollectionListModel(targets)) {
      @Override
      protected PsiElement getPsiElementForHint(final Object selectedValue) {
        return (PsiElement) selectedValue;
      }
    };

    list.setCellRenderer(listRenderer);

    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (listRenderer instanceof PsiElementListCellRenderer) {
      ((PsiElementListCellRenderer)listRenderer).installSpeedSearch(builder);
    }

    final JBPopup popup = builder.
      setTitle(title).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object[] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            PsiElement selected = (PsiElement)element;
            LOG.assertTrue(selected.isValid());
            ((NavigatablePsiElement)selected).navigate(true);
          }
        }
      }).
      setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          list.hideHint();

          return true;
        }
      })
      .createPopup();

    if (appenderTask != null) {
      appenderTask.setList(list);
      appenderTask.setPopup((AbstractPopup)popup);

      ProgressManager.getInstance().run(appenderTask);
    }
    return popup;
  }


  public static abstract class AppenderTask extends Task.Backgroundable {
    private JBListWithHintProvider myList;
    private AbstractPopup myPopup;

    private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private List<PsiElement> myData = new ArrayList<PsiElement>();

    private final Object lock = new Object();

    public AppenderTask(@Nullable final Project project,
                        @NotNull final String title,
                        final boolean canBeCancelled,
                        @Nullable final PerformInBackgroundOption backgroundOption) {
      super(project, title, canBeCancelled, backgroundOption);
    }

    public AppenderTask(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled) {
      super(project, title, canBeCancelled);
    }

    public AppenderTask(@Nullable final Project project, @NotNull final String title) {
      super(project, title);
    }

    public abstract String getCaption(int size);


    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myList.setPaintBusy(true);
    }

    public void setList(JBListWithHintProvider list) {
      myList = list;
    }

    public void setPopup(AbstractPopup popup) {
      myPopup = popup;
    }

    public void updateList(PsiElement element, final PsiElementListCellRenderer renderer) {
      synchronized (lock) {
        myData.add(element);
      }

      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myAlarm.cancelAllRequests();
          final ListModel listModel = myList.getModel();
          synchronized (lock) {
            Collections.sort(myData, renderer.getComparator());
            ((NameFilteringListModel)listModel).replaceAll(myData);
          }
          ((JComponent)myList.getParent()).revalidate();
          myPopup.setSize(myList.getParent().getParent().getPreferredSize());
          myList.repaint();
          myPopup.setCaption(getCaption(getCurrentSize()));
        }
      }, 10, ModalityState.stateForComponent(myList));
    }

    public int getCurrentSize() {
      synchronized (lock) {
        return myData.size();
      }
    }

    @Override
    public void onSuccess() {
      myList.setPaintBusy(false);
      myPopup.setCaption(getCaption(getCurrentSize()));
    }
  }
}
