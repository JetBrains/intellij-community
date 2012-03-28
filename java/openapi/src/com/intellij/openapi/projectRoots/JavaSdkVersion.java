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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Represents version of Java SDK. Use {@link JavaSdk#getVersion(Sdk)} method to obtain version of an {@link Sdk}
 *
 * @author nik
 */
public enum JavaSdkVersion {
  JDK_1_0(LanguageLevel.JDK_1_3, "1.0"), JDK_1_1(LanguageLevel.JDK_1_3, "1.1"), JDK_1_2(LanguageLevel.JDK_1_3, "1.2"), JDK_1_3(LanguageLevel.JDK_1_3, "1.3"),
  JDK_1_4(LanguageLevel.JDK_1_4, "1.4"),
  JDK_1_5(LanguageLevel.JDK_1_5, "1.5"),
  JDK_1_6(LanguageLevel.JDK_1_6, "1.6"),
  JDK_1_7(LanguageLevel.JDK_1_7, "1.7"),
  JDK_1_8(LanguageLevel.JDK_1_8, "1.8");
  private final LanguageLevel myMaxLanguageLevel;
  private final String myDescription;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel, @NotNull String description) {
    myMaxLanguageLevel = maxLanguageLevel;
    myDescription = description;
  }

  @NotNull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  @Override
  public String toString() {
    return super.toString() + ", description: " + myDescription;
  }

  @NotNull
  public static JavaSdkVersion byDescription(@NotNull String description) throws IllegalArgumentException {
    for (JavaSdkVersion version : values()) {
      if (version.getDescription().equals(description)) {
        return version;
      }
    }
    throw new IllegalArgumentException(
      String.format("Can't map Java SDK by description (%s). Available values: %s", description, Arrays.toString(values()))
    );
  }

  public static boolean isAtLeast(PsiElement element, JavaSdkVersion minVersion) {
    final Module module = ModuleUtil.findModuleForPsiElement(element);
    if (module != null) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
        final JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
        return version != null && version.isAtLeast(minVersion);
      }
    }
    return false;
  }
}
