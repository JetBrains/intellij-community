// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileTemplatesScheme implements Scheme {
  public static final FileTemplatesScheme DEFAULT = new FileTemplatesScheme(IdeCoreBundle.message("default.scheme")) {
    @Override
    public @NotNull String getTemplatesDir() {
      return PathManager.getConfigDir().resolve(TEMPLATES_DIR).toString();
    }

    @Override
    public @NotNull Project getProject() {
      return ProjectManager.getInstance().getDefaultProject();
    }
  };

  public static final String TEMPLATES_DIR = "fileTemplates";

  private final @Nls String myName;

  // used externally
  @SuppressWarnings("WeakerAccess")
  public FileTemplatesScheme(@NotNull @Nls String name) {
    myName = name;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public abstract @NotNull String getTemplatesDir();

  public abstract @NotNull Project getProject();
}