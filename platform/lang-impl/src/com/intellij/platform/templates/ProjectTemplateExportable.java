// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.components.ExportableComponent;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class ProjectTemplateExportable implements ExportableComponent {

  @Override
  public File @NotNull [] getExportFiles() {
    return new File[]{new File(ArchivedTemplatesFactory.getCustomTemplatesPath())};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return LangBundle.message("project.template.presentable.name");
  }
}
