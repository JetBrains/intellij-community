/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JavaModuleSourceRoot extends DetectedProjectRoot {
  private List<String> myLanguages;
  private String myPackagePrefix;

  public JavaModuleSourceRoot(File directory, @Nullable String packagePrefix, @NotNull String language) {
    super(directory);
    myLanguages = new ArrayList<String>();
    myLanguages.add(language);
    myPackagePrefix = packagePrefix;
  }

  public JavaModuleSourceRoot(File directory, String packagePrefix, Collection<String> languages) {
    this(directory, packagePrefix, new ArrayList<String>(languages));
  }

  private JavaModuleSourceRoot(File directory, String packagePrefix, List<String> languages) {
    super(directory);
    myLanguages = languages;
    myPackagePrefix = packagePrefix;
  }

  @NotNull
  public String getPackagePrefix() {
    return StringUtil.notNullize(myPackagePrefix);
  }

  @NotNull
  @Override
  public String getRootTypeName() {
    return StringUtil.join(myLanguages, ", ");
  }

  @Override
  public DetectedProjectRoot combineWith(@NotNull DetectedProjectRoot root) {
    if (root instanceof JavaModuleSourceRoot) {
      return new JavaModuleSourceRoot(getDirectory(), myPackagePrefix, ContainerUtil.concat(myLanguages, ((JavaModuleSourceRoot)root).myLanguages));
    }
    return null;
  }
}
