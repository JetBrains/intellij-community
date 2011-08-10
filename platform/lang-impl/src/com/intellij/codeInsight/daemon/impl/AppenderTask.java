/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* User: anna
*/
public abstract class AppenderTask extends Task.Backgroundable {
  private JBList myList;
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

  public void setList(JBList list) {
    myList = list;
  }

  public void setPopup(AbstractPopup popup) {
    myPopup = popup;
  }

  public void updateList(PsiElement element, @Nullable final Comparator comparator) {
    synchronized (lock) {
      myData.add(element);
    }

    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        myAlarm.cancelAllRequests();
        final ListModel listModel = myList.getModel();
        synchronized (lock) {
          if (comparator != null) {
            Collections.sort(myData, comparator);
          }
          ((NameFilteringListModel)listModel).replaceAll(myData);
        }
        ((JComponent)myList.getParent().getParent()).revalidate();
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
