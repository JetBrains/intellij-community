// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.documentation.render.DocRenderManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public final class DocumentationSettingsListener implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus()
      .connect()
      .subscribe(AdvancedSettingsChangeListener.TOPIC, new AdvancedSettingsChangeListener() {
        @Override
        public void advancedSettingChanged(@NotNull String id, @NotNull Object oldValue, @NotNull Object newValue) {
          if (StringUtil.startsWith(id, "documentation.components")) {
            DocRenderManager.resetAllEditorsToDefaultState();
          }
        }
      });
  }
}


