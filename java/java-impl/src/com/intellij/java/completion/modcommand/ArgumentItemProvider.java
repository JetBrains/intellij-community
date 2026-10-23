// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Completes a call of a {@link String} method together with its typical argument after a {@code String} qualifier:
 * {@code toLowerCase(Locale.ROOT)}, {@code toUpperCase(Locale.ROOT)} and {@code getBytes(StandardCharsets.UTF_8)}.
 * The smart completion offers only the calls that match the expected type.
 */
@NotNullByDefault
final class ArgumentItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  private static final List<String> LOOKUP_STRINGS =
    List.of("toLowerCase(Locale.ROOT)", "toUpperCase(Locale.ROOT)", "getBytes(StandardCharsets.UTF_8)");

  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!(context.getPosition() instanceof PsiIdentifier identifier)) return;
    if (!(identifier.getParent() instanceof PsiReferenceExpression ref) || ref instanceof PsiMethodReferenceExpression) return;
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier == null) return;
    if (!ContainerUtil.exists(LOOKUP_STRINGS, context.matcher()::prefixMatches)) return;
    PsiType type = qualifier.getType();
    if (!TypeUtils.isJavaLangString(type) || !PsiUtil.isLanguageLevel7OrHigher(ref)) return;
    PsiClass stringClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (stringClass == null) return;
    PsiMethod toLowerCase = findMethod(stringClass, "toLowerCase", "java.util.Locale");
    PsiMethod toUpperCase = findMethod(stringClass, "toUpperCase", "java.util.Locale");
    PsiMethod getBytes = findMethod(stringClass, "getBytes", "java.nio.charset.Charset");
    if (toLowerCase == null || toUpperCase == null || getBytes == null) return;
    List<MethodWithArgument> calls = List.of(
      new MethodWithArgument(toLowerCase, type, "java.util.Locale.ROOT", "Locale.ROOT"),
      new MethodWithArgument(toUpperCase, type, "java.util.Locale.ROOT", "Locale.ROOT"),
      new MethodWithArgument(getBytes, PsiTypes.byteType().createArrayType(), "java.nio.charset.StandardCharsets.UTF_8",
                             "StandardCharsets.UTF_8"));
    List<ExpectedTypeInfo> expectedTypes =
      context.getCompletionType() == CompletionType.SMART ? List.of(ExpectedTypesProvider.getExpectedTypes(ref, true)) : List.of();
    for (MethodWithArgument call : calls) {
      if (expectedTypes.isEmpty() || ContainerUtil.exists(expectedTypes, info -> info.getType().isAssignableFrom(call.type()))) {
        sink.accept(call.toItem());
      }
    }
  }

  private static @Nullable PsiMethod findMethod(PsiClass stringClass, String name, String parameterTypeName) {
    return ContainerUtil.find(stringClass.findMethodsByName(name, false), m -> {
      PsiParameter[] parameters = m.getParameterList().getParameters();
      return parameters.length == 1 && TypeUtils.typeEquals(parameterTypeName, parameters[0].getType());
    });
  }

  /**
   * A call of {@code method} with the fully qualified {@code argument}, presented with the short {@code argumentPresentation}.
   * {@code type} is the type of the call.
   */
  private record MethodWithArgument(PsiMethod method, PsiType type, String argument, @NlsSafe String argumentPresentation) {
    CommonCompletionItem toItem() {
      String lookupString = method.getName() + "(" + argumentPresentation + ")";
      return new CommonCompletionItem(lookupString)
        .withObject(method)
        .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(lookupString))
                            .withMainIcon(() -> method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                            .withDetailText(MarkupText.plainText(type.getPresentableText())))
        .withPriority(1)
        .withAdditionalUpdater(this::insertArgument);
    }

    /** Replaces the inserted call with the call of the qualified {@code argument}, and shortens the argument with an import. */
    private void insertArgument(int completionStart, ModPsiUpdater updater) {
      PsiFile file = updater.getPsiFile();
      String callSpace = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_METHOD_CALL_PARENTHESES ? " " : "";
      String callText = method.getName() + "(" + callSpace + argument + callSpace + ")";
      Document document = updater.getDocument();
      document.replaceString(completionStart, updater.getCaretOffset(), callText);
      PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
      JavaCodeStyleManager.getInstance(updater.getProject()).shortenClassReferences(file, completionStart, completionStart + callText.length());
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(file.findElementAt(completionStart), PsiMethodCallExpression.class);
      if (call != null) {
        updater.moveCaretTo(call.getTextRange().getEndOffset());
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
