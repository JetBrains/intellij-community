// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;

public class JsonWidgetProvider extends AbstractProjectComponent {
  private JsonSchemaStatusWidget myWidget;

  protected JsonWidgetProvider(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    myWidget = new JsonSchemaStatusWidget(myProject);
  }

  @Override
  public void projectClosed() {
    myWidget.destroy();
  }
}
