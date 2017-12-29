/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class BackgroundUpdaterTask<T> extends Task.Backgroundable {
  protected AbstractPopup myPopup;
  protected T myComponent;
  private Ref<UsageView> myUsageView;
  private final Collection<PsiElement> myData;

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Object lock = new Object();

  private volatile boolean myCanceled;
  private volatile boolean myFinished;
  private volatile ProgressIndicator myIndicator;

  /**
   * @deprecated, use {@link #BackgroundUpdaterTask(Project, String, Comparator)} instead
   */
  @Deprecated
  public BackgroundUpdaterTask(Project project, @NotNull String title) {
    super(project, title);
    myData = ContainerUtil.newSmartList();
  }
  
  public BackgroundUpdaterTask(@Nullable Project project, @NotNull String title, @Nullable Comparator<PsiElement> comparator) {
    super(project, title);
    myData = comparator == null ? ContainerUtil.newSmartList() : new TreeSet<>(comparator);
  }

  public void init(@NotNull AbstractPopup popup, @NotNull T component, @NotNull Ref<UsageView> usageView) {
    myPopup = popup;
    myComponent = component;
    myUsageView = usageView;
  }

  public abstract String getCaption(int size);
  protected abstract void replaceModel(@NotNull List<PsiElement> data);

  public static Comparator<PsiElement> createComparatorWrapper(@NotNull Comparator<PsiElement> comparator) {
    return (o1, o2) -> {
      int diff = comparator.compare(o1, o2);
      if (diff == 0) {
        return ReadAction.compute(() -> PsiUtilCore.compareElementsByPosition(o1, o2));
      }
      return diff;
    };
  }

  protected abstract void paintBusy(boolean paintBusy);

  public boolean setCanceled() {
    boolean canceled = myCanceled;
    myCanceled = true;
    return canceled;
  }

  public boolean isCanceled() {
    return myCanceled;
  }

  /**
   * @deprecated, use {@link #BackgroundUpdaterTask(Project, String, Comparator)} and {@link #updateComponent(PsiElement)} instead
   */
  public boolean updateComponent(@NotNull PsiElement element, @Nullable Comparator comparator) {
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
      if (comparator != null && myData instanceof List) {
        Collections.sort(((List)myData), comparator);
      }
    }

    myAlarm.addRequest(() -> {
      myAlarm.cancelAllRequests();
      refreshModelImmediately();
    }, 200, ModalityState.stateForComponent(content));
    return true;
  }
  
  public boolean updateComponent(@NotNull PsiElement element) {
    final UsageView view = myUsageView.get();
    if (view != null && !((UsageViewImpl)view).isDisposed()) {
      ApplicationManager.getApplication().runReadAction(() -> view.appendUsage(new UsageInfo2UsageAdapter(new UsageInfo(element))));
      return true;
    }

    if (myCanceled) return false;
    final JComponent content = myPopup.getContent();
    if (content == null || myPopup.isDisposed()) return false;

    synchronized (lock) {
      if (!myData.add(element)) return true;
    }

    myAlarm.addRequest(() -> {
      myAlarm.cancelAllRequests();
      refreshModelImmediately();
    }, 200, ModalityState.stateForComponent(content));
    return true;
  }

  private void refreshModelImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myCanceled) return;
    if (myPopup.isDisposed()) return;
    List<PsiElement> data;
    synchronized (lock) {
      data = new ArrayList<>(myData);
    }
    replaceModel(data);
    myPopup.setCaption(getCaption(getCurrentSize()));
    myPopup.pack(true, true);
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
    myFinished = true;
    refreshModelImmediately();
    paintBusy(false);
  }

  @Override
  public void onFinished() {
    myAlarm.cancelAllRequests();
    myFinished = true;
  }

  @Nullable
  protected PsiElement getTheOnlyOneElement() {
    synchronized (lock) {
      if (myData.size() == 1) {
        return myData.iterator().next();
      }
    }
    return null;
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
