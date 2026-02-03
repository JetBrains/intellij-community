// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectTemplateFileProcessor {

  public static final ExtensionPointName<ProjectTemplateFileProcessor> EP_NAME = ExtensionPointName.create("com.intellij.projectTemplateFileProcessor");

  /** Return null if it can't be processed */
  protected abstract @Nullable String encodeFileText(String content, VirtualFile file, Project project) throws IOException;

  public static String encodeFile(String content, VirtualFile file, Project project) throws IOException {
    ProjectTemplateFileProcessor[] processors = EP_NAME.getExtensions();
    for (ProjectTemplateFileProcessor processor : processors) {
      String text = processor.encodeFileText(content, file, project);
      if (text != null) return text;
    }
    return content;
  }

  protected static String wrap(String param) {
    return "${" + param + "}";
  }
}
