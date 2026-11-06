// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Completes class literals where a {@code Class<...>} is expected: {@code Class<? extends Number> c = Integ<caret>}
 * gets {@code Integer.class}.
 */
@NotNullByDefault
final class ClassLiteralItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  private static final @NonNls String DOT_CLASS = ".class";

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    PsiElement position = context.getPosition();
    if (!(position instanceof PsiIdentifier) ||
        JavaCompletionUtil.AFTER_DOT.accepts(position) ||
        JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
      return;
    }
    boolean smart = context.isSmart();
    if (smart && (ReferenceItemProvider.inCastContext(context) ||
                  !JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) ||
                  !(position.getParent() instanceof PsiJavaCodeReferenceElement))) {
      return;
    }
    Map<PsiType, ExpectedTypeInfo> infoByType = new LinkedHashMap<>();
    Set<PsiType> typesWithSeveralTails = new HashSet<>();
    for (ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(position, smart)) {
      ExpectedTypeInfo previous = infoByType.putIfAbsent(info.getType(), info);
      if (previous != null && !previous.getTailType().equals(info.getTailType())) {
        typesWithSeveralTails.add(info.getType());
      }
    }
    PsiClass javaLangClass = JavaPsiFacade.getInstance(position.getProject())
      .findClass(CommonClassNames.JAVA_LANG_CLASS, position.getResolveScope());
    if (javaLangClass == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(position.getProject());
    boolean genericClass = javaLangClass.getTypeParameters().length == 1;
    for (ExpectedTypeInfo info : infoByType.values()) {
      boolean useExprTailType = smart && !typesWithSeveralTails.contains(info.getType());
      ModNavigatorTailType tail = useExprTailType ? info.getTailType() : null;
      processClassLiteralTypes(info, position, context.matcher(), type -> {
        if (smart) {
          PsiClassType literalType = genericClass ? factory.createType(javaLangClass, type) : factory.createType(javaLangClass);
          if (!info.getType().isAssignableFrom(literalType)) return;
        }
        CommonCompletionItem item = createItem(type, smart ? 0 : 1);
        sink.accept(tail == null ? item : item.withTail(tail));
      });
    }
  }

  /**
   * Passes the types whose class literals fit an expected {@code Class<...>} type at {@code position}:
   * the type argument and, for {@code ? extends T}, its inheritors. With a typed prefix, a raw {@code Class} or a type variable
   * argument such as {@code Class<T>} gets every class that matches the prefix.
   */
  private static void processClassLiteralTypes(ExpectedTypeInfo info,
                                               PsiElement position,
                                               PrefixMatcher matcher,
                                               Consumer<? super PsiClassType> consumer) {
    PsiType expectedType = info.getDefaultType();
    if (!InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_CLASS)) {
      expectedType = info.getType();
      if (!InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_CLASS)) {
        return;
      }
    }

    PsiType classParameter = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_LANG_CLASS, 0, false);

    boolean addInheritors = false;
    if (classParameter instanceof PsiWildcardType wildcardType) {
      classParameter = wildcardType.isSuper() ? wildcardType.getSuperBound() : wildcardType.getExtendsBound();
      addInheritors = !wildcardType.isSuper() && classParameter instanceof PsiClassType &&
                      !(matcher.getPrefix().isEmpty() && TypeUtils.isJavaLangObject(classParameter));
    }
    else if (!matcher.getPrefix().isEmpty() && !isConcreteClass(classParameter)) {
      addInheritors = true;
      classParameter = PsiType.getJavaLangObject(position.getManager(), position.getResolveScope());
    }
    if (classParameter != null) {
      processClassLiteralType(classParameter, consumer);
      if (addInheritors) {
        CodeInsightUtil.processSubTypes(classParameter, position.getContainingFile(), true, matcher,
                                        type -> processClassLiteralType(type, consumer));
      }
    }
  }

  private static boolean isConcreteClass(@Nullable PsiType type) {
    PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return psiClass != null && !(psiClass instanceof PsiTypeParameter);
  }

  private static void processClassLiteralType(@Nullable PsiType type, Consumer<? super PsiClassType> consumer) {
    if (type instanceof PsiClassType classType && !classType.hasParameters()) {
      PsiClass psiClass = classType.resolve();
      if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
        consumer.accept(classType);
      }
    }
  }

  private static CommonCompletionItem createItem(PsiClassType type, double priority) {
    PsiClass psiClass = Objects.requireNonNull(type.resolve());
    @NlsSafe String lookupString = type.getPresentableText() + DOT_CLASS;
    String canonicalText = type.getCanonicalText();
    String packageName = StringUtil.getPackageName(canonicalText);
    MarkupText text = MarkupText.plainText(lookupString);
    if (!packageName.isEmpty()) {
      text = text.concat(" (" + packageName + ")", MarkupText.Kind.GRAYED);
    }
    return new CommonCompletionItem(lookupString)
      .withObject(psiClass)
      .withPresentation(new ModCompletionItemPresentation(text).withMainIcon(() -> psiClass.getIcon(0)))
      .withPriority(priority)
      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)
      .withAdditionalUpdater((completionStart, updater) -> insertClassLiteral(canonicalText, completionStart, updater));
  }

  private static void insertClassLiteral(String canonicalText, int completionStart, ModPsiUpdater updater) {
    Document document = updater.getDocument();
    int offset = updater.getCaretOffset();
    String replacement = canonicalText + DOT_CLASS;
    if (document.getTextLength() >= offset + DOT_CLASS.length() &&
        document.getText(TextRange.from(offset, DOT_CLASS.length())).equals(DOT_CLASS)) {
      replacement = canonicalText;
    }
    document.replaceString(completionStart, offset, replacement);
    PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
    updater.moveCaretTo(completionStart + canonicalText.length() + DOT_CLASS.length());
    JavaCompletionUtil.shortenReference(updater.getPsiFile(), completionStart + canonicalText.length() - 1);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
