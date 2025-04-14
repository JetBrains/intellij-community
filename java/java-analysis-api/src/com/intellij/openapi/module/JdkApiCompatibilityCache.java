// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a compatibility matrix for JDK APIs. The matrix is generated from {@code @since} tags in the JDK source code.
 */
@Service(Service.Level.APP)
public final class JdkApiCompatibilityCache {
  private static final Logger LOG = Logger.getInstance(JdkApiCompatibilityCache.class);

  private final Map<LanguageLevel, List<String>> cache = new ConcurrentHashMap<>();

  public static JdkApiCompatibilityCache getInstance() {
    return ApplicationManager.getApplication().getService(JdkApiCompatibilityCache.class);
  }

  /**
   * Finds the first compatible language level for a given {@code member} that is incompatible given the {@code contextLanguageLevel} or
   * null if the {@code member} is compatible with the provided {@code contextLanguageLevel}. This method uses a pre-generated JDK
   * compatability matrix that is generated from {@code @since} tags in the JDK source code, meaning that the JDK Javadoc doesn't need to
   * be available for this method to work correctly.
   * <p>
   * Examples:
   * <ul>
   *   <li>if {@code member} is annotated as {@code @since 9} and the context language level is {@link LanguageLevel#JDK_1_8} this method
   *   will return {@link LanguageLevel#JDK_1_9}.</li>
   *
   *   <li>if {@code member} is annotated as {@code @since 10} and the context language level is {@link LanguageLevel#JDK_11} this method
   *   will return {@code null}.
   *
   *   <li>if {@code member} is method annotated as {@code @since 9} and the context language level is {@link LanguageLevel#JDK_1_8}, but
   *   its super method is annotated as {@code @since 8} this method will return null.
   *
   *   <li>if {@code member} is method annotated as {@code @since 9} and the context language level is {@link LanguageLevel#JDK_11}, but
   *   its super method is annotated as {@code @since 8} this method will return {@link LanguageLevel#JDK_1_8}.
   *
   *   <li>if {@code member} is not annotated with a {@code @since} tag this method will return null.
   * </ul>
   *
   * @param member               The member to find the incompatible language level for
   * @param contextLanguageLevel The current language level of the context of {@code member}
   * @return The first compatible language level for {@code member} or null if it is lower or equal than {@code contextLanguageLevel} or
   * unknown.
   */
  public @Nullable LanguageLevel firstCompatibleLanguageLevel(@NotNull PsiMember member, @NotNull LanguageLevel contextLanguageLevel) {
    if (member instanceof PsiAnonymousClass) return null;
    PsiClass containingClass = member.getContainingClass();
    if (containingClass instanceof PsiAnonymousClass) return null;
    if (member instanceof PsiClass clazz && PsiUtil.isLocalClass(clazz)) return null;

    List<PsiMember> membersToCheck = new ArrayList<>();
    membersToCheck.add(member);
    if (member instanceof PsiMethod method && !method.isConstructor()) {
      membersToCheck.addAll(Arrays.asList(method.findSuperMethods()));
    }

    LanguageLevel incompatibleLevelForContext = LanguageLevelUtil.getNextLanguageLevel(contextLanguageLevel);
    LanguageLevel lowestCompatibleLanguageLevel = null;
    for (PsiMember checkMember : membersToCheck) {
      String signature = getSignature(checkMember);
      if (signature == null) return null;
      LanguageLevel compatibleLanguageLevelForMember = getIntroducedApiLevel(signature, incompatibleLevelForContext);
      if (compatibleLanguageLevelForMember == null) return null;
      if (lowestCompatibleLanguageLevel == null || compatibleLanguageLevelForMember.isLessThan(lowestCompatibleLanguageLevel)) {
        lowestCompatibleLanguageLevel = compatibleLanguageLevelForMember;
      }
    }
    return lowestCompatibleLanguageLevel;
  }

  /**
   * Gets all the newly introduced APIs for a given {@code languageLevel} and caches the results.
   *
   * @param languageLevel to get the newly introduced APis for.
   * @return the newly introduced APIs in {@code  languageLevel} or empty set if the language level is not supported.
   */
  private @NotNull List<String> getIntroducedApis(@NotNull LanguageLevel languageLevel) {
    return cache.computeIfAbsent(languageLevel, level -> {
      String featureString = level.toJavaVersion().toFeatureString();
      List<String> result = Collections.emptyList();
      URL resource = JdkApiCompatibilityCache.class.getResource("api" + featureString + ".txt");
      if (resource != null) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
          result = FileUtil.loadLines(reader);
        }
        catch (IOException ex) {
          LOG.error("Cannot load: " + resource.getFile(), ex);
        }
      }
      return result;
    });
  }

  /**
   * @param signature     The signature, example: "java.util.Iterator#remove()" as specified by {@link #getSignature(PsiMember)}.
   * @param languageLevel to start the search.
   * @return The newly introduced API if it appears after or including {@code languageLevel}, or null if it was introduced before
   * {@code languageLevel}.
   */
  private LanguageLevel getIntroducedApiLevel(@NotNull String signature, @Nullable LanguageLevel languageLevel) {
    if (languageLevel == null) return null;
    if (getIntroducedApis(languageLevel).contains(signature)) return languageLevel;
    return getIntroducedApiLevel(signature, LanguageLevelUtil.getNextLanguageLevel(languageLevel));
  }

  /**
   * Serializes a {@code member} for storage in apiX.txt files.
   * <p>
   * Example: {@code java.net.URLDecoder#decode(java.lang.String;java.nio.charset.Charset;)}
   */
  @ApiStatus.Internal
  public @Nullable String getSignature(@Nullable PsiMember member) {
    if (member instanceof PsiClass psiClass) return psiClass.getQualifiedName();
    if (member instanceof PsiField) {
      String containingClass = getSignature(member.getContainingClass());
      return containingClass == null ? null : containingClass + "#" + member.getName();
    }
    if (member instanceof PsiMethod method) {
      String containingClass = getSignature(member.getContainingClass());
      if (containingClass == null) return null;

      StringBuilder buf = new StringBuilder();
      buf.append(containingClass);
      buf.append('#');
      buf.append(method.getName());
      buf.append('(');
      for (PsiType type : method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes()) {
        buf.append(type.getCanonicalText());
        buf.append(";");
      }
      buf.append(')');
      return buf.toString();
    }
    return null;
  }
}
