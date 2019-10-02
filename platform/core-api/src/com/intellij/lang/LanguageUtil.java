// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.LanguageSubstitutors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class LanguageUtil {
  private LanguageUtil() {
  }

  public static final Comparator<Language> LANGUAGE_COMPARATOR =
    (o1, o2) -> StringUtil.naturalCompare(o1.getDisplayName(), o2.getDisplayName());


  @Nullable
  public static Language getLanguageForPsi(@NotNull Project project, @Nullable VirtualFile file) {
    Language language = getFileLanguage(file);
    if (language == null) return null;
    return LanguageSubstitutors.getInstance().substituteLanguage(language, file, project);
  }

  @Nullable
  public static Language getFileLanguage(@Nullable VirtualFile file) {
    if (file == null) return null;
    Language l = file instanceof LightVirtualFile? ((LightVirtualFile)file).getLanguage() : null;
    return l != null ? l : getFileTypeLanguage(file.getFileType());
  }

  @Nullable
  public static Language getFileTypeLanguage(@Nullable FileType fileType) {
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
  }

  @Nullable
  public static FileType getLanguageFileType(@Nullable Language language) {
    return language == null ? null : language.getAssociatedFileType();
  }

  public static ParserDefinition.SpaceRequirements canStickTokensTogetherByLexer(ASTNode left, ASTNode right, Lexer lexer) {
    String textStr = left.getText() + right.getText();

    lexer.start(textStr, 0, textStr.length());
    if (lexer.getTokenType() != left.getElementType()) return ParserDefinition.SpaceRequirements.MUST;
    if (lexer.getTokenEnd() != left.getTextLength()) return ParserDefinition.SpaceRequirements.MUST;

    lexer.advance();
    if (lexer.getTokenEnd() != textStr.length()) return ParserDefinition.SpaceRequirements.MUST;
    if (lexer.getTokenType() != right.getElementType()) return ParserDefinition.SpaceRequirements.MUST;

    return ParserDefinition.SpaceRequirements.MAY;
  }

  @NotNull
  public static Set<Language> getAllDerivedLanguages(@NotNull Language base) {
    Set<Language> result = new HashSet<>();
    getAllDerivedLanguages(base, result);
    return result;
  }

  private static void getAllDerivedLanguages(Language base, Set<Language> result) {
    result.add(base);
    for (Language dialect : base.getDialects()) {
      getAllDerivedLanguages(dialect, result);
    }
  }

  public static boolean isInTemplateLanguageFile(@Nullable final PsiElement element) {
    if (element == null) return false;

    final PsiFile psiFile = element.getContainingFile();
    if(psiFile == null) return false;

    final Language language = psiFile.getViewProvider().getBaseLanguage();
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
    if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) {
      return false;
    }
    return true;
  }

  public static boolean isFileLanguage(@NotNull Language language) {
    if (language instanceof DependentLanguage || language instanceof InjectableLanguage) return false;
    if (LanguageParserDefinitions.INSTANCE.forLanguage(language) == null) return false;
    LanguageFileType type = language.getAssociatedFileType();
    if (type == null || StringUtil.isEmpty(type.getDefaultExtension())) return false;
    return StringUtil.isNotEmpty(type.getDefaultExtension());
  }

  @NotNull
  public static List<Language> getFileLanguages() {
    LanguageParserDefinitions.INSTANCE.ensureValuesLoaded();
    List<Language> result = new ArrayList<>();
    for (Language language : Language.getRegisteredLanguages()) {
      if (!isFileLanguage(language)) continue;
      result.add(language);
    }
    result.sort(LANGUAGE_COMPARATOR);
    return result;
  }

  @NotNull
  public static Language getRootLanguage(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    final FileViewProvider provider = containingFile.getViewProvider();
    final Set<Language> languages = provider.getLanguages();
    if (languages.size() > 1) {
      final Language language = containingFile.getLanguage();
      if (languages.contains(language)) {
        return language;
      }
    }
    return provider.getBaseLanguage();
  }

  private static final Key<Collection<MetaLanguage>> MATCHING_LANGUAGES = Key.create("language.matching");

  @NotNull
  static Collection<MetaLanguage> matchingMetaLanguages(@NotNull Language language) {
    if (!Extensions.getRootArea().hasExtensionPoint(MetaLanguage.EP_NAME)) {
      return Collections.emptyList(); // don't cache
    }

    Collection<MetaLanguage> cached = language.getUserData(MATCHING_LANGUAGES);
    if (cached != null) {
      return cached;
    }
    Set<MetaLanguage> result = MetaLanguage.getAllMatchingMetaLanguages(language).collect(Collectors.toSet());
    return language.putUserDataIfAbsent(MATCHING_LANGUAGES, result);
  }

  @NotNull
  static JBIterable<Language> hierarchy(@NotNull Language language) {
    return JBIterable.generate(language, Language::getBaseLanguage);
  }
}
