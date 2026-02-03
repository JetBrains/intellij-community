// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class UsagesPanel extends JPanel implements Disposable, UiDataProvider {
  protected static final Logger LOG = Logger.getInstance(UsagesPanel.class);

  private final Project myProject;
  ProgressIndicator myCurrentProgress;
  private JComponent myCurrentComponent;
  private UsageView myCurrentUsageView;
  protected final Alarm myAlarm = new Alarm();

  public UsagesPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
  }

  public void setToInitialPosition() {
    cancelCurrentFindRequest();
    setToComponent(createLabel(getInitialPositionText()));
  }

  public abstract @Nls String getInitialPositionText();
  public abstract @Nls String getCodeUsagesString();

  void cancelCurrentFindRequest() {
    if (myCurrentProgress != null) {
      myCurrentProgress.cancel();
    }
  }

  protected void showUsages(PsiElement @NotNull [] primaryElements, UsageInfo @NotNull [] usageInfos) {
    if (myCurrentUsageView != null) {
      Disposer.dispose(myCurrentUsageView);
    }
    try {
      Usage[] usages = UsageInfoToUsageConverter.convert(primaryElements, usageInfos);
      UsageViewPresentation presentation = new UsageViewPresentation();
      presentation.setCodeUsagesString(getCodeUsagesString());
      myCurrentUsageView = UsageViewManager.getInstance(myProject).createUsageView(UsageTarget.EMPTY_ARRAY, usages, presentation, null);
      ((UsageViewImpl)myCurrentUsageView).expandRoot();
      setToComponent(myCurrentUsageView.getComponent());
    }
    catch (ProcessCanceledException e) {
      setToCanceled();
    }
  }

  private void setToCanceled() {
    setToComponent(createLabel(CodeInsightBundle.message("usage.view.canceled")));
  }

  final void setToComponent(@NotNull JComponent component) {
    AppUIExecutor.onWriteThread(ModalityState.any()).expireWith(myProject).execute(() -> {
      if (myCurrentComponent != null) {
        if (myCurrentUsageView != null && myCurrentComponent == myCurrentUsageView.getComponent()){
          Disposer.dispose(myCurrentUsageView);
          myCurrentUsageView = null;
        }
        remove(myCurrentComponent);
      }
      myCurrentComponent = component;
      add(component, BorderLayout.CENTER);
      revalidate();
    });
  }

  @Override
  public void dispose() {
    if (myCurrentUsageView != null) {
      Disposer.dispose(myCurrentUsageView);
      myCurrentUsageView = null;
    }
  }

  private static JComponent createLabel(@Nls String text) {
    JLabel label = new JLabel(text);
    label.setHorizontalAlignment(SwingConstants.CENTER);
    return label;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformCoreDataKeys.HELP_ID, "ideaInterface.find");
  }
}