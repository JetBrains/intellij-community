/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class InvalidateCachesAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(ApplicationManager.getApplication().isRestartCapable() ? "Invalidate Caches / Restart..." : "Invalidate Caches...");
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    final boolean mac = Messages.canShowMacSheetPanel();
    boolean canRestart = app.isRestartCapable();
    
    String[] options = new String[canRestart ? 4 : 3];
    options[0] = canRestart ? "Invalidate and &Restart" : "Invalidate and &Exit";
    options[1] = mac ? "Cancel" : "&Invalidate";
    options[2] = mac ? "&Invalidate" : "Cancel";
    if (canRestart) {
      options[3] = "&Just Restart";
    }

    List<String> descriptions = new SmartList<>();
    boolean invalidateCachesInvalidatesVfs = Registry.is("idea.invalidate.caches.invalidates.vfs");

    if (invalidateCachesInvalidatesVfs) descriptions.add("Local History");

    for (CachesInvalidator invalidater : CachesInvalidator.EP_NAME.getExtensions()) {
      ContainerUtil.addIfNotNull(descriptions, invalidater.getDescription());
    }
    Collections.sort(descriptions);
    
    String warnings = "WARNING: ";
    if (descriptions.size() == 1) {
      warnings += descriptions.get(0) + " will be also cleared.";
    }
    else if (!descriptions.isEmpty()) {
      warnings += "The following items will also be cleared:\n"
                  + StringUtil.join(descriptions, s -> "  " + s, "\n");
    }
    
    String message = "The caches will be invalidated and rebuilt on the next startup.\n\n" +
                     (descriptions.isEmpty() ? "" :  warnings + "\n\n") +
                     "Would you like to continue?\n";
    int result = Messages.showDialog(e.getData(CommonDataKeys.PROJECT),
                                     message,
                                     "Invalidate Caches",
                                     options, 0,
                                     Messages.getWarningIcon());

    if (result == -1 || result == (mac ? 1 : 2)) {
      return;
    }
    
    if (result == 3) {
      app.restart(true);
      return;
    }

    UsageTrigger.trigger(ApplicationManagerEx.getApplicationEx().getName() + ".caches.invalidated");
    if (invalidateCachesInvalidatesVfs) FSRecords.invalidateCaches();
    else FileBasedIndex.getInstance().invalidateCaches();

    for (CachesInvalidator invalidater : CachesInvalidator.EP_NAME.getExtensions()) {
      invalidater.invalidateCaches();
    }

    if (result == 0) app.restart(true);
  }
}
