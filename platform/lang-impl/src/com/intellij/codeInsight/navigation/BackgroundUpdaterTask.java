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
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
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
public abstract class BackgroundUpdaterTask<T> extends Task.Backgroundable {
  protected AbstractPopup myPopup;
  protected T myComponent;
  private Ref<UsageView> myUsageView;
  private final List<PsiElement> myData = new ArrayList<>();

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Object lock = new Object();

  private volatile boolean myCanceled;
  private volatile boolean myFinished;
  private volatile ProgressIndicator myIndicator;

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

  public void init(@NotNull AbstractPopup popup, @NotNull T component, @NotNull Ref<UsageView> usageView) {
    myPopup = popup;
    myComponent = component;
    myUsageView = usageView;

  }

  public abstract String getCaption(int size);
  protected abstract void replaceModel(@NotNull List<PsiElement> data);
  protected abstract void paintBusy(boolean paintBusy);

  public boolean setCanceled() {
    boolean canceled = myCanceled;
    myCanceled = true;
    return canceled;
  }

  public boolean isCanceled() {
    return myCanceled;
  }

  public boolean updateComponent(final PsiElement element, @Nullable final Comparator comparator) {
    final UsageView view = myUsageView.get();
    if (view != null && !((UsageViewImpl)view).isDisposed()) {
      ApplicationManager.getApplication().runReadAction(() -> view.appendUsage(new UsageInfo2UsageAdapter(new UsageInfo(element))));
      return true;
    }

    if (myCanceled) return false;
    final JComponent content = myPopup.getContent();
    if (content == null || myPopup.isDisposed()) return false;

    synchronized (lock) {
      if (myData.contains(element)) return true;
      myData.add(element);
    }

    myAlarm.addRequest(() -> {
      myAlarm.cancelAllRequests();
      if (myCanceled) return;
      if (myPopup.isDisposed()) return;
      ArrayList<PsiElement> data = new ArrayList<>();
      synchronized (lock) {
        if (comparator != null) {
          Collections.sort(myData, comparator);
        }
        data.addAll(myData);
      }
      replaceModel(data);
      myPopup.setCaption(getCaption(getCurrentSize()));
      myPopup.pack(true, true);
    }, 200, ModalityState.stateForComponent(content));
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
    myIndicator = indicator;
  }

  @Override
  public void onSuccess() {
    myPopup.setCaption(getCaption(getCurrentSize()));
    paintBusy(false);
  }

  @Override
  public void onFinished() {
    myFinished = true;
  }

  public boolean isFinished() {
    return myFinished;
  }

  public boolean cancelTask() {
    ProgressIndicator indicator = myIndicator;
    if (indicator != null) {
      indicator.cancel();
    }
    return setCanceled();
  }
}
