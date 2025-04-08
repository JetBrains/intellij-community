// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;
import static kotlinx.collections.immutable.ExtensionsKt.toPersistentList;

public final class LanguageUtil {
  private LanguageUtil() {
  }

  public static final Comparator<Language> LANGUAGE_COMPARATOR =
    (o1, o2) -> NaturalComparator.INSTANCE.compare(o1.getDisplayName(), o2.getDisplayName());

  public static @Nullable Language getLanguageForPsi(@NotNull Project project, @Nullable VirtualFile file) {
    return getLanguageForPsi(project, file, null);
  }

  public static @Nullable Language getLanguageForPsi(@NotNull Project project, @Nullable VirtualFile file, @Nullable FileType fileType) {
    if (file == null) {
      return null;
    }

    // a copy-paste of getFileLanguage(file)
    Language explicit = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getLanguage() : null;
    Language fileLanguage = explicit != null ? explicit : getFileTypeLanguage(fileType != null ? fileType : file.getFileType());

    if (fileLanguage == null) return null;
    // run generic file-level substitutors, e.g. for scratches
    for (LanguageSubstitutor substitutor : LanguageSubstitutors.getInstance().forKey(Language.ANY)) {
      Language language = substitutor.getLanguage(file, project);
      if (language != null && language != Language.ANY) {
        fileLanguage = language;
        break;
      }
    }
    return LanguageSubstitutors.getInstance().substituteLanguage(fileLanguage, file, project);
  }

  public static @Nullable Language getFileLanguage(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }

    Language language = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getLanguage() : null;
    return language == null ? getFileTypeLanguage(file.getFileType()) : language;
  }

  public static @Nullable Language getFileTypeLanguage(@NotNull FileType fileType) {
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
  }

  public static @Nullable FileType getLanguageFileType(@Nullable Language language) {
    return language == null ? null : language.getAssociatedFileType();
  }

  public static @NotNull ParserDefinition.SpaceRequirements canStickTokensTogetherByLexer(@NotNull ASTNode left,
                                                                                          @NotNull ASTNode right,
                                                                                          @NotNull Lexer lexer) {
    String textStr = left.getText() + right.getText();

    lexer.start(textStr, 0, textStr.length());
    if (lexer.getTokenType() != left.getElementType() || lexer.getTokenEnd() != left.getTextLength()) {
      return ParserDefinition.SpaceRequirements.MUST;
    }

    lexer.advance();
    if (lexer.getTokenEnd() != textStr.length() || lexer.getTokenType() != right.getElementType()) {
      return ParserDefinition.SpaceRequirements.MUST;
    }

    return ParserDefinition.SpaceRequirements.MAY;
  }

  /**
   * @deprecated see {@link Language#getTransitiveDialects()}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static @NotNull @Unmodifiable Set<Language> getAllDerivedLanguages(@NotNull Language base) {
    Set<Language> result = new HashSet<>();
    result.add(base);
    result.addAll(base.getTransitiveDialects());
    return result;
  }

  public static boolean isInTemplateLanguageFile(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      return false;
    }

    Language language = psiFile.getViewProvider().getBaseLanguage();
    return language instanceof TemplateLanguage;
  }

  public static boolean isInjectableLanguage(@NotNull Language language) {
    if (language == Language.ANY) {
      return false;
    }
    if (language.getID().startsWith("$")) {
      return false;
    }
    if (language instanceof InjectableLanguage) {
      return true;
    }
    if (language instanceof TemplateLanguage || language instanceof DependentLanguage) {
      return false;
    }
    return LanguageParserDefinitions.INSTANCE.forLanguage(language) != null;
  }

  public static @NotNull @Unmodifiable List<Language> getInjectableLanguages() {
    return getLanguages(LanguageUtil::isInjectableLanguage);
  }

  public static boolean isFileLanguage(@NotNull Language language) {
    if (language instanceof DependentLanguage ||
        language instanceof InjectableLanguage ||
        LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
      return false;
    }

    LanguageFileType type = language.getAssociatedFileType();
    return type != null && !Strings.isEmpty(type.getDefaultExtension());
  }

  public static @NotNull @Unmodifiable List<Language> getFileLanguages() {
    return getLanguages(LanguageUtil::isFileLanguage);
  }

  public static @NotNull @Unmodifiable List<Language> getLanguages(@NotNull Predicate<? super Language> filter) {
    LanguageParserDefinitions.INSTANCE.ensureValuesLoaded();
    List<Language> result = new ArrayList<>();
    for (Language language : Language.getRegisteredLanguages()) {
      if (filter.test(language)) {
        result.add(language);
      }
    }
    result.sort(LANGUAGE_COMPARATOR);
    return result;
  }

  public static @NotNull Language getRootLanguage(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    FileViewProvider provider = containingFile.getViewProvider();
    Set<Language> languages = provider.getLanguages();
    if (languages.size() > 1) {
      Language language = containingFile.getLanguage();
      if (languages.contains(language)) {
        return language;
      }
    }
    return provider.getBaseLanguage();
  }

  private static final Key<List<MetaLanguage>> MATCHING_META_LANGUAGES = Key.create("MATCHING_META_LANGUAGES");

  static @NotNull @Unmodifiable List<MetaLanguage> matchingMetaLanguages(@NotNull Language language) {
    List<MetaLanguage> cached = language.getUserData(MATCHING_META_LANGUAGES);
    if (cached != null) {
      return cached;
    }

    if (!ApplicationManager.getApplication().getExtensionArea().hasExtensionPoint(MetaLanguage.EP_NAME)) {
      // don't cache
      return persistentListOf();
    }

    List<MetaLanguage> toCache;
    if (language instanceof MetaLanguage) {
      toCache = persistentListOf();
    }
    else {
      Set<MetaLanguage> result = new HashSet<>();
      MetaLanguage.EP_NAME.forEachExtensionSafe(metaLanguage -> {
        if (metaLanguage.matchesLanguage(language)) {
          result.add(metaLanguage);
        }
      });
      toCache = result.isEmpty() ? persistentListOf() : toPersistentList(result);
    }
    language.putUserData(MATCHING_META_LANGUAGES, toCache);
    return toCache;
  }

  static void clearMatchingMetaLanguagesCache(@NotNull Language language) {
    language.putUserData(MATCHING_META_LANGUAGES, null);
  }

  public static @NotNull JBIterable<Language> getBaseLanguages(@NotNull Language language) {
    return JBIterable.generate(language, Language::getBaseLanguage);
  }

  public static @Nullable Language findRegisteredLanguage(@NotNull String langValueText) {
    Language language = Language.findLanguageByID(langValueText);
    if (language != null) {
      return language;
    }

    for (Language value : Language.getRegisteredLanguages()) {
      if (value.getID().equalsIgnoreCase(langValueText) || value.getDisplayName().equalsIgnoreCase(langValueText)) {
        return value;
      }
    }
    return null;
  }
}
