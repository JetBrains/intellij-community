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
  public final static FileTemplatesScheme DEFAULT = new FileTemplatesScheme(IdeCoreBundle.message("default.scheme")) {
    @NotNull
    @Override
    public String getTemplatesDir() {
      return PathManager.getConfigDir().resolve(TEMPLATES_DIR).toString();
    }

    @NotNull
    @Override
    public Project getProject() {
      return ProjectManager.getInstance().getDefaultProject();
    }
  };

  public static final String TEMPLATES_DIR = "fileTemplates";

  private final @Nls String myName;

  public FileTemplatesScheme(@NotNull @Nls String name) {
    myName = name;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public abstract String getTemplatesDir();

  @NotNull
  public abstract Project getProject();
}