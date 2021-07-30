// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.EdtDataContext;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessDialog;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.List;

public class IdeUiServiceImpl extends IdeUiService{
  @Override
  public void revealFile(File file) {
    RevealFileAction.openFile(file);
  }

  @Override
  public NonProjectFileWritingAccessProvider.UnlockOption askForUnlock(@NotNull Project project,
                                                                       List<VirtualFile> files) {
    NonProjectFileWritingAccessDialog dialog = new NonProjectFileWritingAccessDialog(project, files);
    if (!dialog.showAndGet()) return null;
    return dialog.getUnlockOption();
  }

  @Override
  public boolean isFileRecentlyChanged(Project project, VirtualFile file) {
    IdeDocumentHistoryImpl documentHistory = (IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(project);
    if (documentHistory.isRecentlyChanged(file)) return true;
    return false;
  }

  @Override
  public void logUsageEvent(Class clazz, String groupId, String eventId) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    String factoryClass = pluginInfo.isSafeToReport() ? clazz.getName() : "third.party";
    FeatureUsageData data = new FeatureUsageData().addData("factory", factoryClass).addPluginInfo(pluginInfo);
    FUCounterUsageLogger.getInstance().logEvent(groupId, eventId, data);
  }

  @Override
  public void systemNotify(String title, String text) {
    SystemNotifications.getInstance().notify("SessionLogger", title, StringUtil.stripHtml(text, true));
  }

  @Override
  public DataContext createUiDataContext(Component component) {
    return new EdtDataContext(component);
  }

  @Override
  public Component getComponentFromRecentMouseEvent() {
    return SwingHelper.getComponentFromRecentMouseEvent();
  }
}
