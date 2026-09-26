// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.SmartList;
import com.siyeh.ig.format.FormatDecode;
import com.siyeh.ig.format.FormatDecode.FormatArgument;
import com.siyeh.ig.format.FormatDecode.FormatSpecifier;
import com.siyeh.ig.format.FormatDecode.FormatSpecifiers;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.InjectorUtils.InjectionInfo;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Injects a language into the format string of {@code String.format(...)} / {@code String.formatted(...)}, splitting it
 * at the format specifiers ({@code %s}, {@code %d}, ...) and bridging each gap with the corresponding argument value, so
 * the string is treated like the equivalent {@code "..." + arg + "..."} concatenation.
 */
public final class JavaFormatInjector implements MultiHostInjector {
  private static final String MISSING_VALUE = "missingValue";

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return List.of(PsiLiteralExpression.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof PsiLiteralExpression literal) || !(context instanceof PsiLanguageInjectionHost host)) return;
    PsiExpression[] formatArgs = formatArguments(literal);
    if (formatArgs == null) return;

    Project project = context.getProject();
    PsiFile containingFile = literal.getContainingFile();

    TemporaryPlacesRegistry tempRegistry = TemporaryPlacesRegistry.getInstance(project);
    InjectedLanguage tempInjectedLanguage = tempRegistry.getLanguageFor(host, containingFile);
    Language tempLanguage = tempInjectedLanguage == null ? null : tempInjectedLanguage.getLanguage();
    LanguageInjectionSupport injectionSupport = tempLanguage == null
                                                ? InjectorUtils.findNotNullInjectionSupport(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)
                                                : tempRegistry.getLanguageInjectionSupport();
    Configuration configuration = Configuration.getProjectInstance(project);

    ConcatenationInjector.InjectionProcessor processor =
      new ConcatenationInjector.InjectionProcessor(configuration, injectionSupport, true, literal) {
        @Override
        protected Pair<PsiLanguageInjectionHost, Language> processInjection(Language language,
                                                                            List<InjectionInfo> list,
                                                                            boolean settingsAvailable,
                                                                            boolean unparsable) {
          boolean frankenstein = unparsable;
          if (!list.isEmpty()) {
            FormatSplit split = splitFormatInjection(configuration, list, formatArgs);
            if (split != null) {
              list.clear();
              list.addAll(split.infos());
              frankenstein |= split.unparsable();
            }
          }
          boolean isFrankenstein = frankenstein;
          InjectorUtils.registerInjection(
            language, containingFile, list, registrar, r -> {
              r.putInjectedFileUserData(LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, tempInjectedLanguage)
                .frankensteinInjection(isFrankenstein);
              InjectorUtils.registerSupport(r, injectionSupport, settingsAvailable);
            }
          );
          return Pair.create(list.getFirst().host(), language);
        }
      };
    processor.processInjections();
  }

  /**
   * Splits the resolved injection ranges at the format specifiers, bridging each gap with the corresponding argument
   * value (or {@link #MISSING_VALUE} when not computable). The implicit-argument count is threaded across the infos so
   * a multi-piece host (e.g. a text block) keeps numbering arguments continuously.
   * Returns {@code null} when there are no specifiers to split on, so the caller keeps the original list as a plain injection.
   * When the format string is malformed (a specifier cannot be parsed), returns the original (unsplit) infos with {@code unparsable == true}, so
   * the caller keeps the whole-string injection but marks it frankenstein.
   */
  private static @Nullable FormatSplit splitFormatInjection(@NotNull Configuration configuration,
                                                            @NotNull List<InjectionInfo> infos,
                                                            PsiExpression @NotNull [] args) {
    String languageId = infos.getFirst().language().getID();
    String cfgPrefix = infos.getFirst().language().getPrefix();
    String cfgSuffix = infos.getLast().language().getSuffix();

    List<FormatPiece> pieces = new ArrayList<>();
    int implicitBase = 0;
    boolean anySpecifier = false;
    boolean unparsable = false;
    for (InjectionInfo info : infos) {
      PsiLanguageInjectionHost host = info.host();
      TextRange range = info.range();
      String rangeText = range.substring(host.getText());
      FormatSpecifiers specifiers = FormatDecode.getFormatSpecifiers(rangeText, implicitBase);
      if (specifiers == null) {
        // Malformed format string (e.g. a positional index that overflows int): the text cannot be reliably
        // reconstructed, so keep the original whole-string injection but flag it frankenstein to suppress highlighting.
        return new FormatSplit(List.copyOf(infos), true);
      }
      int segmentStart = 0;
      for (FormatSpecifier specifier : specifiers.specifiers()) {
        anySpecifier = true;
        Bridge bridge = specifier.literalReplacement() != null
                        ? new Bridge(specifier.literalReplacement(), false)
                        : bridgeValue(configuration, args, specifier.argumentIndex());
        unparsable |= bridge.unparsable();
        TextRange fragment = new TextRange(range.getStartOffset() + segmentStart,
                                           range.getStartOffset() + specifier.range().getStartOffset());
        pieces.add(new FormatPiece(host, fragment, bridge.value()));
        segmentStart = specifier.range().getEndOffset();
      }
      pieces.add(new FormatPiece(host, new TextRange(range.getStartOffset() + segmentStart, range.getEndOffset()), null));
      implicitBase = specifiers.nextImplicit();
    }
    if (!anySpecifier) return null;

    List<InjectionInfo> result = new ArrayList<>(pieces.size());
    for (int i = 0; i < pieces.size(); i++) {
      FormatPiece piece = pieces.get(i);
      String prefix = i == 0 ? cfgPrefix : "";
      String suffix = piece.bridge() != null ? piece.bridge() : (i == pieces.size() - 1 ? cfgSuffix : "");
      result.add(new InjectionInfo(piece.host(), InjectedLanguage.create(languageId, prefix, suffix, true), piece.range()));
    }
    return new FormatSplit(result, unparsable);
  }

  private static @NotNull Bridge bridgeValue(@NotNull Configuration configuration,
                                             PsiExpression @NotNull [] args,
                                             int argumentIndex) {
    if (argumentIndex < 0 || argumentIndex >= args.length) {
      return new Bridge(MISSING_VALUE, true);
    }
    PsiExpression arg = args[argumentIndex];
    SmartList<PsiExpression> uncomputables = new SmartList<>();
    Configuration.AdvancedConfiguration advanced = configuration.getAdvancedConfiguration();
    Object value = new SubstitutedExpressionEvaluationHelper(arg.getProject())
      .computeExpression(arg, advanced.getDfaOption(), advanced.isIncludeUncomputablesAsLiterals(), uncomputables);
    return new Bridge(value == null ? MISSING_VALUE : String.valueOf(value), !uncomputables.isEmpty());
  }

  /** Result of {@link #splitFormatInjection}: the re-split pieces and whether any argument was missing/uncomputable.
   *  For a malformed format string the original (unsplit) infos are returned with {@code unparsable == true}
   */
  private record FormatSplit(@NotNull List<InjectionInfo> infos, boolean unparsable) {
  }

  private record Bridge(@NotNull String value, boolean unparsable) {
  }

  private record FormatPiece(@NotNull PsiLanguageInjectionHost host, @NotNull TextRange range, @Nullable String bridge) {
  }

  /**
   * Trailing arguments of the enclosing {@code String.format(...)} / {@code String.formatted(...)} call whose format
   * string is {@code literal}, or {@code null} if it is not such a call
   */
  private static PsiExpression @Nullable [] formatArguments(@NotNull PsiLiteralExpression literal) {
    PsiMethodCallExpression call = enclosingCall(literal);
    if (call == null || !isStringFormatCall(call)) return null;
    FormatArgument format = FormatArgument.extract(call, Collections.emptyList(), Collections.emptyList(), true);
    if (format == null || format.getExpression() != literal) return null;
    PsiExpression[] all = call.getArgumentList().getExpressions();
    int from = format.getIndex();
    return from >= 0 && from <= all.length ? Arrays.copyOfRange(all, from, all.length) : PsiExpression.EMPTY_ARRAY;
  }

  private static @Nullable PsiMethodCallExpression enclosingCall(@NotNull PsiLiteralExpression literal) {
    PsiElement parent = literal.getParent();
    // instance call: "...".formatted(args)
    if (parent instanceof PsiReferenceExpression ref &&
        ref.getQualifierExpression() == literal &&
        ref.getParent() instanceof PsiMethodCallExpression call &&
        call.getMethodExpression() == ref) {
      return call;
    }
    // static call: String.format([locale,] "...", args)
    if (parent instanceof PsiExpressionList argumentList && argumentList.getParent() instanceof PsiMethodCallExpression call) {
      return call;
    }
    return null;
  }

  private static boolean isStringFormatCall(@NotNull PsiMethodCallExpression call) {
    String name = call.getMethodExpression().getReferenceName();
    if (!"format".equals(name) && !"formatted".equals(name)) return false;
    PsiMethod method = call.resolveMethod();
    PsiClass containingClass = method == null ? null : method.getContainingClass();
    return containingClass != null && CommonClassNames.JAVA_LANG_STRING.equals(containingClass.getQualifiedName());
  }
}
