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
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class ProjectSetProcessor {

  public static final ExtensionPointName<ProjectSetProcessor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.projectSetProcessor");

  public static final String PROJECT = "project";

  public abstract String getId();

  public abstract void processEntries(@NotNull List<? extends Pair<String, String>> entries, @NotNull Context context, @NotNull Runnable runNext);

  public static class Context {
    public VirtualFile directory;
    public String directoryName;
    public Project project;
  }
}
