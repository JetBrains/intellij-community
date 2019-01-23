// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.project.Project;

public class CommandFactory {

  public enum Type {
    DOWNLOAD,
    EXTRACT,
    COPY,
    UPDATE_PATH,
    DELETE
  }

  private final Project myProject;

  private CommandFactory(Project project) {
    myProject = project;
  }

  private static CommandFactory instance;

  public static void initialize(Project project) {
    instance = new CommandFactory(project);
  }

  public static CommandFactory getInstance() {
    if (instance == null) throw new IllegalStateException("Command Factory has not been initialized");
    return instance;
  }

  public static Command produce(Type commandType, Runtime runtime) {
    switch (commandType) {
      case DOWNLOAD:
        return new Download(getInstance().myProject, runtime);
      case EXTRACT:
        return new Extract(getInstance().myProject, runtime);
      case COPY:
        return new Copy(getInstance().myProject, runtime);
      case UPDATE_PATH:
        return new UpdatePath(getInstance().myProject, runtime);
      case DELETE:
        return new Delete(getInstance().myProject, runtime);
    }
    throw new IllegalStateException("Unknown Command Type");
  }
}
