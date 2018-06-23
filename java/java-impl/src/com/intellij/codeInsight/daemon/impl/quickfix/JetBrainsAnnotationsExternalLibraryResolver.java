/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author nik
 */
public class JetBrainsAnnotationsExternalLibraryResolver extends ExternalLibraryResolver {
  /**
   * Specifies version of jetbrains-annotations library which will be selected by default when user applies a quick fix on an unresolved annotation reference.
   * It must be equal to version of jetbrains-annotations library which is bundled with the IDE, the both should refer to version of the library
   * which is fully supported by the current state of IDE's inspections.
   */
  private static final String VERSION = "16.0.2";
  private static final ExternalLibraryDescriptor JAVA5 = new ExternalLibraryDescriptor("org.jetbrains", "annotations-java5",
                                                                                       null, null, VERSION);
  private static final ExternalLibraryDescriptor JAVA8 = new ExternalLibraryDescriptor("org.jetbrains", "annotations",
                                                                                       null, null, VERSION);

  @Nullable
  @Override
  public ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (AnnotationUtil.isJetbrainsAnnotation(shortClassName)) {
      ExternalLibraryDescriptor libraryDescriptor = getAnnotationsLibraryDescriptor(contextModule);
      return new ExternalClassResolveResult("org.jetbrains.annotations." + shortClassName, libraryDescriptor);
    }
    return null;
  }

  @NotNull
  public static ExternalLibraryDescriptor getAnnotationsLibraryDescriptor(@NotNull Module contextModule) {
    boolean java8 = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(contextModule).isAtLeast(LanguageLevel.JDK_1_8);
    return java8 ? JAVA8 : JAVA5;
  }

  @TestOnly
  public static String getVersion() {
    return VERSION;
  }
}
