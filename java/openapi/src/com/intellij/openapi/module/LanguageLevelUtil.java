/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class LanguageLevelUtil {
  private LanguageLevelUtil() {
  }

  @NotNull
  public static LanguageLevel getEffectiveLanguageLevel(@NotNull final Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LanguageLevel level = LanguageLevelModuleExtension.getInstance(module).getLanguageLevel();
    if (level != null) return level;
    return LanguageLevelProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }

  @NotNull
  public static LanguageLevel getLanguageLevelForFile(final VirtualFile file) {
    if (file == null) return LanguageLevel.HIGHEST;

    if (file.isDirectory()) {
      final LanguageLevel ll = file.getUserData(LanguageLevel.KEY);
      return ll != null ? ll : LanguageLevel.HIGHEST;
    }

    return getLanguageLevelForFile(file.getParent());
  }

  public static LanguageLevel getDefaultLanguageLevel(@NotNull String versionString) {
    if (isOfVersionOrHigher(versionString, "1.7") || isOfVersionOrHigher(versionString, "7.0")) {
      return LanguageLevel.JDK_1_7;
    }
    if (isOfVersionOrHigher(versionString, "1.6") || isOfVersionOrHigher(versionString, "6.0")) {
      return LanguageLevel.JDK_1_6;
    }
    if (isOfVersionOrHigher(versionString, "1.5") || isOfVersionOrHigher(versionString, "5.0")) {
      return LanguageLevel.JDK_1_5;
    }

    if (isOfVersionOrHigher(versionString, "1.4")) {
      return LanguageLevel.JDK_1_4;
    }

    return LanguageLevel.JDK_1_3;
  }

  public static boolean isOfVersionOrHigher(@NotNull String versionString, String checkedVersion) {
    return JavaSdk.getInstance().compareTo(versionString, checkedVersion) >= 0;
  }
}
