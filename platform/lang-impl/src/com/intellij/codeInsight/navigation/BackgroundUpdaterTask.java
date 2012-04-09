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
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 */
public abstract class BackgroundUpdaterTask<T> extends Task.Backgroundable {
  protected AbstractPopup myPopup;
  protected T myComponent;
  private List<PsiElement> myData = new ArrayList<PsiElement>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Object lock = new Object();

  private volatile boolean myCanceled = false;

  public BackgroundUpdaterTask(Project project, String title, boolean canBeCancelled) {
    super(project, title, canBeCancelled);
  }

  public BackgroundUpdaterTask(Project project, String title) {
    super(project, title);
  }

  public BackgroundUpdaterTask(Project project,
                               String title,
                               boolean canBeCancelled,
                               PerformInBackgroundOption backgroundOption) {
    super(project, title, canBeCancelled, backgroundOption);
  }

  public void init(AbstractPopup popup, T component) {
    myPopup = popup;
    myComponent = component;

    myPopup.addPopupListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        setCanceled();
      }
    });
  }

  public abstract String getCaption(int size);
  protected abstract void replaceModel(@NotNull List<PsiElement> data);
  protected abstract void paintBusy(boolean paintBusy);

  public boolean setCanceled() {
    boolean canceled = myCanceled;
    myCanceled = true;
    return canceled;
  }

  public boolean updateComponent(PsiElement element, @Nullable final Comparator comparator) {
    if (myCanceled) return false;
    if (myPopup.isDisposed()) return false;

    synchronized (lock) {
      if (myData.contains(element)) return true;
      myData.add(element);
    }

    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        myAlarm.cancelAllRequests();
        if (myCanceled) return;
        if (myPopup.isDisposed()) return;
        ArrayList<PsiElement> data = new ArrayList<PsiElement>();
        synchronized (lock) {
          if (comparator != null) {
            Collections.sort(myData, comparator);
          }
          data.addAll(myData);
        }
        replaceModel(data);
        myPopup.setCaption(getCaption(getCurrentSize()));
        myPopup.pack(true, true);
      }
    }, 200, ModalityState.stateForComponent(myPopup.getContent()));
    return true;
  }

  public int getCurrentSize() {
    synchronized (lock) {
      return myData.size();
    }
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    paintBusy(true);
  }

  @Override
  public void onSuccess() {
    myPopup.setCaption(getCaption(getCurrentSize()));
    paintBusy(false);
  }
}
