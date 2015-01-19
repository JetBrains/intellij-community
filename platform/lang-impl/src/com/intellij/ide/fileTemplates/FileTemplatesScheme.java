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
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileTemplatesScheme implements Scheme {

  public final static FileTemplatesScheme DEFAULT = new FileTemplatesScheme("Default") {
    @NotNull
    @Override
    public String getTemplatesDir() {
      return new File(PathManager.getConfigPath(), TEMPLATES_DIR).getPath();
    }
  };

  public static final String TEMPLATES_DIR = "fileTemplates";

  private final String myName;

  public FileTemplatesScheme(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public abstract String getTemplatesDir();
}
