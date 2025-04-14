// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LanguageLevelUtil {
  private LanguageLevelUtil() { }

  /**
   * @param module to get the language level for.
   * @return explicitly specified language level for a {@link Module}, or {@code null} if the module uses 'Project default' language level.
   * May return an {@linkplain LanguageLevel#isUnsupported() unsupported} language level.
   */
  public static @Nullable LanguageLevel getCustomLanguageLevel(@NotNull Module module) {
    LanguageLevelModuleExtension moduleExtension =
      ModuleRootManager.getInstance(module).getModuleExtension(LanguageLevelModuleExtension.class);
    return moduleExtension != null ? moduleExtension.getLanguageLevel() : null;
  }

  /**
   * @param module to get the language level for.
   * @return the effective language level for a {@link Module}, which is either the overridden language level for the module or the project
   * language level.
   * May return {@linkplain LanguageLevel#isUnsupported() unsupported} language level.
   */
  public static @NotNull LanguageLevel getEffectiveLanguageLevel(@NotNull Module module) {
    LanguageLevel level = getCustomLanguageLevel(module);
    if (level != null) return level;
    return LanguageLevelProjectExtension.getInstance(module.getProject()).getLanguageLevel();
  }

  /**
   * @deprecated Please use {@link LanguageLevel#getShortText()} instead.
   */
  @Deprecated(forRemoval = true)
  public static String getJdkName(LanguageLevel languageLevel) {
    return languageLevel.getShortText();
  }

  /**
   * @param languageLevel The language level to get the next from.
   * @return Next {@link LanguageLevel} that is not in preview or null if there is no language level.
   */
  public static @Nullable LanguageLevel getNextLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return LanguageLevel.forFeature(languageLevel.feature() + 1);
  }

  /**
   * @param languageLevel The language level to get the previous from.
   * @return Previous {@link LanguageLevel} that is not in preview or null if there is no language level.
   */
  public static @Nullable LanguageLevel getPrevLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return LanguageLevel.forFeature(languageLevel.feature() - 1);
  }

  /**
   * Retrieves the short language-level name like "17" for Java 17 or "1.5" for Java 1.5.
   *
   * @param languageLevel The language level for which to retrieve the short name.
   * @return The short name associated with the specified language level, or null if the language level is not released yet.
   * @deprecated Please use {@code languageLevel.toJavaVersion().toFeatureString()} instead.
   */
  @Deprecated(forRemoval = true)
  public static @NotNull String getShortMessage(@NotNull LanguageLevel languageLevel) {
    return languageLevel.toJavaVersion().toFeatureString();
  }

  /**
   * @param member        The {@link PsiMember} to get the language level from
   * @param languageLevel The effective language level
   * @return The last incompatible language level for a {@link PsiMember} as annotated by the @since Javadoc or null if it is unknown.
   * For example, if a method is annotated as @since 9 this method will return {@link LanguageLevel#JDK_1_8}.
   * @deprecated Please use {@code JdkIncompatibleApiCache.getInstance().getLastIncompatibleLanguageLevel(member, languageLevel)}
   */
  @Deprecated(forRemoval = true)
  public static @Nullable LanguageLevel getLastIncompatibleLanguageLevel(@NotNull PsiMember member, @NotNull LanguageLevel languageLevel) {
    LanguageLevel firstCompatibleLanguageLevel = JdkApiCompatibilityCache.getInstance().firstCompatibleLanguageLevel(member, languageLevel);
    if (firstCompatibleLanguageLevel == null) return null;
    return getPrevLanguageLevel(firstCompatibleLanguageLevel);
  }

  /**
   * @deprecated Please use {@link JdkApiCompatibilityCache} to check for incompatible APIs.
   */
  @Deprecated(forRemoval = true)
  public static Set<String> loadSignatureList(@NotNull URL resource) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
      return new HashSet<>(FileUtil.loadLines(reader));
    }
    catch (IOException ex) {
      Logger.getInstance(LanguageLevelUtil.class).warn("cannot load: " + resource.getFile(), ex);
      return Collections.emptySet();
    }
  }

  /**
   * For serialization of forbidden api.
   *
   * @deprecated Please don't use this, this API was moved to {@link JdkApiCompatibilityCache} and is for internal use only.
   */
  @Deprecated(forRemoval = true)
  public static @Nullable String getSignature(@Nullable PsiMember member) {
    return JdkApiCompatibilityCache.getInstance().getSignature(member);
  }
}
