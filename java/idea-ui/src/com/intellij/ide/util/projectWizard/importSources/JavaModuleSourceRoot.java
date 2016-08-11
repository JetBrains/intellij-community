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
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JavaModuleSourceRoot extends DetectedSourceRoot {
  private List<String> myLanguages;

  public JavaModuleSourceRoot(File directory, @Nullable String packagePrefix, @NotNull String language) {
    super(directory, packagePrefix);
    myLanguages = new ArrayList<>();
    myLanguages.add(language);
  }

  private JavaModuleSourceRoot(File directory, String packagePrefix, List<String> languages) {
    super(directory, packagePrefix);
    myLanguages = languages;
  }

  @NotNull
  @Override
  public String getRootTypeName() {
    return StringUtil.join(myLanguages, ", ");
  }

  @Override
  public DetectedProjectRoot combineWith(@NotNull DetectedProjectRoot root) {
    if (root instanceof JavaModuleSourceRoot) {
      return combineWith((JavaModuleSourceRoot)root);
    }
    return null;
  }

  @NotNull
  public JavaModuleSourceRoot combineWith(@NotNull JavaModuleSourceRoot root) {
    List<String> union = new ArrayList<>(myLanguages.size() + root.myLanguages.size());
    union.addAll(myLanguages);
    union.addAll(root.myLanguages);
    ContainerUtil.removeDuplicates(union);
    return new JavaModuleSourceRoot(getDirectory(), getPackagePrefix(), union);
  }
}
