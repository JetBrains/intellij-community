// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetProvider;
import org.jetbrains.annotations.NotNull;

public class JsonSchemaStatusWidgetProvider implements StatusBarWidgetProvider {

  @NotNull
  @Override
  public StatusBarWidget getWidget(@NotNull Project project) {
    return new JsonSchemaStatusWidget(project);
  }
}
