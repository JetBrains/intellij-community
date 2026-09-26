// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JavaModuleSourceRoot extends DetectedSourceRoot {
  private final List<@Nls(capitalization = Nls.Capitalization.Sentence) String> myLanguages;
  private final boolean myWithModuleInfoFile; // module-info.java

  public JavaModuleSourceRoot(File directory, @Nullable String packagePrefix, @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String language) {
    super(directory, packagePrefix);
    myLanguages = new ArrayList<>();
    myLanguages.add(language);
    myWithModuleInfoFile = false;
  }

  public JavaModuleSourceRoot(File directory, @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String language, boolean withModuleInfoFile) {
    super(directory, "");
    myLanguages = new ArrayList<>();
    myLanguages.add(language);
    myWithModuleInfoFile = withModuleInfoFile;
  }

  private JavaModuleSourceRoot(File directory, String packagePrefix, List<@Nls(capitalization = Nls.Capitalization.Sentence) String> languages) {
    super(directory, packagePrefix);
    myLanguages = languages;
    myWithModuleInfoFile = false;
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getRootTypeName() {
    @NlsSafe String result = StringUtil.join(myLanguages, ", ");
    return result;
  }

  @Override
  public DetectedProjectRoot combineWith(@NotNull DetectedProjectRoot root) {
    if (root instanceof JavaModuleSourceRoot) {
      return combineWith((JavaModuleSourceRoot)root);
    }
    return null;
  }

  public @NotNull JavaModuleSourceRoot combineWith(@NotNull JavaModuleSourceRoot root) {
    List<@Nls(capitalization = Nls.Capitalization.Sentence) String> union = new ArrayList<>(myLanguages.size() + root.myLanguages.size());
    union.addAll(myLanguages);
    union.addAll(root.myLanguages);
    ContainerUtil.removeDuplicates(union);
    return new JavaModuleSourceRoot(getDirectory(), getPackagePrefix(), union);
  }

  public boolean isWithModuleInfoFile() {
    return myWithModuleInfoFile;
  }
}
