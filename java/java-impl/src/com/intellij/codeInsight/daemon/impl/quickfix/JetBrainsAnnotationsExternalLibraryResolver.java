// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.pom.java.JavaFeature;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class JetBrainsAnnotationsExternalLibraryResolver extends ExternalLibraryResolver {
  /**
   * Specifies version of jetbrains-annotations library which will be selected by default when user applies a quick fix on an unresolved annotation reference.
   * It must be equal to version of jetbrains-annotations library which is bundled with the IDE, the both should refer to version of the library
   * which is fully supported by the current state of IDE's inspections.
   */
  private static final String JAVA5_VERSION = "24.0.0";
  private static final String VERSION = "26.0.2";
  private static final ExternalLibraryDescriptor JAVA5 = new ExternalLibraryDescriptor("org.jetbrains", "annotations-java5",
                                                                                       null, null, JAVA5_VERSION);
  private static final ExternalLibraryDescriptor JAVA8 = new ExternalLibraryDescriptor("org.jetbrains", "annotations",
                                                                                       null, null, VERSION);

  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (AnnotationUtil.isJetbrainsAnnotation(shortClassName)) {
      ExternalLibraryDescriptor libraryDescriptor = getAnnotationsLibraryDescriptor(contextModule);
      return new ExternalClassResolveResult("org.jetbrains.annotations." + shortClassName, libraryDescriptor);
    }
    return null;
  }

  public static @NotNull ExternalLibraryDescriptor getAnnotationsLibraryDescriptor(@NotNull Module contextModule) {
    boolean java8 = JavaFeature.TYPE_ANNOTATIONS.isSufficient(LanguageLevelUtil.getEffectiveLanguageLevel(contextModule));
    return java8 ? JAVA8 : JAVA5;
  }

  @TestOnly
  public static String getVersion() {
    return VERSION;
  }
}
