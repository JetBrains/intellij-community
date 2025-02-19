// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsConditionalFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ChangeNewOperatorTypeFix;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.java.codeserver.core.JavaPsiModifierUtil;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.*;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.tryCast;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {

  private static final @NlsSafe String ANONYMOUS = "anonymous ";

  private HighlightUtil() { }

  private static @NotNull QuickFixFactory getFixFactory() {
    return QuickFixFactory.getInstance();
  }


  static HighlightInfo.Builder checkAssignability(@Nullable PsiType lType,
                                          @Nullable PsiType rType,
                                          @Nullable PsiExpression expression,
                                          @NotNull PsiElement elementToHighlight) {
    TextRange textRange = elementToHighlight.getTextRange();
    if (lType == rType) return null;
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return null;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression) || PsiTreeUtil.hasErrorElements(expression)) {
      return null;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    if (lType == null || lType == PsiTypes.nullType()) {
      return null;
    }
    if (expression != null && IncompleteModelUtil.isIncompleteModel(expression) &&
        IncompleteModelUtil.isPotentiallyConvertible(lType, expression)) {
      return null;
    }
    HighlightInfo.Builder highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange);
    AddTypeArgumentsConditionalFix.register(asConsumer(highlightInfo), expression, lType);
    if (expression != null) {
      AdaptExpressionTypeFixUtil.registerExpectedTypeFixes(asConsumer(highlightInfo), expression, lType, rType);
      if (!(expression.getParent() instanceof PsiConditionalExpression && PsiTypes.voidType().equals(lType))) {
        IntentionAction fix = HighlightFixUtil.createChangeReturnTypeFix(expression, lType);
        if (fix != null) {
          highlightInfo.registerFix(fix, null, null, null, null);
        }
      }
    }
    ModCommandAction fix = ChangeNewOperatorTypeFix.createFix(expression, lType);
    if (fix != null) {
      highlightInfo.registerFix(fix, null, null, null, null);
    }
    return highlightInfo;
  }

  static @NotNull Consumer<CommonIntentionAction> asConsumer(@Nullable HighlightInfo.Builder highlightInfo) {
    if (highlightInfo == null) {
      return fix -> {};
    }
    return fix -> {
      if (fix != null) {
        highlightInfo.registerFix(fix.asIntention(), null, null, null, null);
      }
    };
  }

  public static @NotNull @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  public static @NotNull String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  private static @NotNull String formatField(@NotNull PsiField field) {
    return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
  }

  static @NotNull @NlsContexts.DetailedDescription String staticContextProblemDescription(@NotNull PsiElement refElement) {
    String type = JavaElementKind.fromElement(refElement).lessDescriptive().subject();
    String name = HighlightMessageUtil.getSymbolName(refElement, PsiSubstitutor.EMPTY);
    return JavaErrorBundle.message("non.static.symbol.referenced.from.static.context", type, name);
  }

  static @NotNull @NlsContexts.DetailedDescription String accessProblemDescription(@NotNull PsiElement ref,
                                                                                   @NotNull PsiElement resolved,
                                                                                   @NotNull JavaResolveResult result) {
    return accessProblemDescriptionAndFixes(ref, resolved, result).first;
  }

  static @NotNull Pair<@Nls String, List<IntentionAction>> accessProblemDescriptionAndFixes(@NotNull PsiElement ref,
                                                                                            @NotNull PsiElement resolved,
                                                                                            @NotNull JavaResolveResult result) {
    assert resolved instanceof PsiModifierListOwner : resolved;
    PsiModifierListOwner refElement = (PsiModifierListOwner)resolved;
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("private.symbol", symbolName, containerName), null);
    }

    if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("protected.symbol", symbolName, containerName), null);
    }

    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
      symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    }

    if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("package.local.symbol", symbolName, containerName), null);
    }

    String containerName = getContainerName(refElement, result.getSubstitutor());
    ErrorWithFixes problem = checkModuleAccess(resolved, ref, symbolName, containerName);
    if (problem != null) return Pair.pair(problem.message, problem.fixes);
    return Pair.pair(JavaErrorBundle.message("visibility.access.problem", symbolName, containerName), null);
  }

  static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement resolved, @NotNull PsiElement ref, @NotNull JavaResolveResult result) {
    PsiElement refElement = resolved;
    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
    }

    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    String containerName = (resolved instanceof PsiModifierListOwner modifierListOwner)
                           ? getContainerName(modifierListOwner, result.getSubstitutor())
                           : null;
    return checkModuleAccess(resolved, ref, symbolName, containerName);
  }

  private static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement target,
                                                                 @NotNull PsiElement place,
                                                                 @Nullable String symbolName,
                                                                 @Nullable String containerName) {
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (moduleSystem instanceof JavaModuleSystemEx system) {
        if (target instanceof PsiClass targetClass) {
          final ErrorWithFixes problem = system.checkAccess(targetClass, place);
          if (problem != null) return problem;
        }
        if (target instanceof PsiPackage targetPackage) {
          final ErrorWithFixes problem = system.checkAccess(targetPackage.getQualifiedName(), null, place);
          if (problem != null) return problem;
        }
      }
      else if (!isAccessible(moduleSystem, target, place)) {
        return new ErrorWithFixes(JavaErrorBundle.message("visibility.module.access.problem", symbolName, containerName, moduleSystem.getName()));
      }
    }
    return null;
  }

  private static boolean isAccessible(@NotNull JavaModuleSystem system, @NotNull PsiElement target, @NotNull PsiElement place) {
    if (target instanceof PsiClass psiClass) return system.isAccessible(psiClass, place);
    if (target instanceof PsiPackage psiPackage) return system.isAccessible(psiPackage.getQualifiedName(), null, place);
    return true;
  }

  private static PsiElement getContainer(@NotNull PsiModifierListOwner refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensionList()) {
      PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  private static String getContainerName(@NotNull PsiModifierListOwner refElement, @NotNull PsiSubstitutor substitutor) {
    PsiElement container = getContainer(refElement);
    return container == null ? "?" : HighlightMessageUtil.getSymbolName(container, substitutor);
  }

  static HighlightInfo.@NotNull Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                                            @Nullable PsiType rType,
                                                                            @NotNull TextRange textRange) {
    @NotNull String reason = getReasonForIncompatibleTypes(rType);
    PsiType baseLType = PsiUtil.convertAnonymousToBaseType(lType);
    PsiType baseRType = rType == null ? null : PsiUtil.convertAnonymousToBaseType(rType);
    boolean leftAnonymous = PsiUtil.resolveClassInClassTypeOnly(lType) instanceof PsiAnonymousClass;
    String styledReason = reason.isEmpty() ? "" :
                          String.format("<table><tr><td style=''padding-top: 10px; padding-left: 4px;''>%s</td></tr></table>", reason);
    IncompatibleTypesTooltipComposer tooltipComposer = (lTypeString, lTypeArguments, rTypeString, rTypeArguments) ->
      JavaErrorBundle.message("incompatible.types.html.tooltip",
                              lTypeString, lTypeArguments,
                              rTypeString, rTypeArguments,
                              styledReason, "#" + ColorUtil.toHex(UIUtil.getContextHelpForeground()));
    String toolTip = createIncompatibleTypesTooltip(leftAnonymous ? lType : baseLType, leftAnonymous ? rType : baseRType, tooltipComposer);

    String lTypeString = JavaHighlightUtil.formatType(leftAnonymous ? lType : baseLType);
    String rTypeString = JavaHighlightUtil.formatType(leftAnonymous ? rType : baseRType);
    String description = JavaErrorBundle.message("incompatible.types", lTypeString, rTypeString);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(textRange)
      .description(description)
      .escapedToolTip(toolTip)
      .navigationShift(0);
  }

  /**
   * @param elementToHighlight element to attach the highlighting
   * @return HighlightInfo builder that adds a pending reference highlight
   */
  static HighlightInfo.@NotNull Builder getPendingReferenceHighlightInfo(@NotNull PsiElement elementToHighlight) {
    return HighlightInfo.newHighlightInfo(HighlightInfoType.PENDING_REFERENCE).range(elementToHighlight)
      .descriptionAndTooltip(JavaErrorBundle.message("incomplete.project.state.pending.reference"));
  }

  @FunctionalInterface
  interface IncompatibleTypesTooltipComposer {
    @NotNull @NlsContexts.Tooltip
    String consume(@NotNull @NlsSafe String lRawType,
                   @NotNull @NlsSafe String lTypeArguments,
                   @NotNull @NlsSafe String rRawType,
                   @NotNull @NlsSafe String rTypeArguments);

    /**
     * Override if expected/actual pair layout is a row
     */
    default boolean skipTypeArgsColumns() {
      return false;
    }
  }

  static @NotNull @NlsContexts.Tooltip String createIncompatibleTypesTooltip(PsiType lType,
                                                                             PsiType rType,
                                                                             @NotNull IncompatibleTypesTooltipComposer consumer) {
    TypeData lTypeData = typeData(lType);
    TypeData rTypeData = typeData(rType);
    PsiTypeParameter[] lTypeParams = lTypeData.typeParameters();
    PsiTypeParameter[] rTypeParams = rTypeData.typeParameters();

    int typeParamColumns = Math.max(lTypeParams.length, rTypeParams.length);
    boolean skipColumns = consumer.skipTypeArgsColumns();
    StringBuilder requiredRow = new StringBuilder();
    StringBuilder foundRow = new StringBuilder();
    for (int i = 0; i < typeParamColumns; i++) {
      PsiTypeParameter lTypeParameter = i >= lTypeParams.length ? null : lTypeParams[i];
      PsiTypeParameter rTypeParameter = i >= rTypeParams.length ? null : rTypeParams[i];
      PsiType lSubstitutedType = lTypeParameter == null ? null : lTypeData.substitutor().substitute(lTypeParameter);
      PsiType rSubstitutedType = rTypeParameter == null ? null : rTypeData.substitutor().substitute(rTypeParameter);
      boolean matches = lSubstitutedType == rSubstitutedType ||
                        lSubstitutedType != null &&
                        rSubstitutedType != null &&
                        TypeConversionUtil.typesAgree(lSubstitutedType, rSubstitutedType, true);
      String openBrace = i == 0 ? "&lt;" : "";
      String closeBrace = i == typeParamColumns - 1 ? "&gt;" : ",";
      boolean showShortType = showShortType(lSubstitutedType, rSubstitutedType);

      requiredRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 8px 0px;'>")
        .append(lTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(lSubstitutedType, true, showShortType))
        .append(i < lTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");

      foundRow.append(skipColumns ? "" : "<td style='padding: 0px 0px 0px 0px;'>")
        .append(rTypeParams.length == 0 ? "" : openBrace)
        .append(redIfNotMatch(rSubstitutedType, matches, showShortType))
        .append(i < rTypeParams.length ? closeBrace : "")
        .append(skipColumns ? "" : "</td>");
    }
    PsiType lRawType = lType instanceof PsiClassType classType ? classType.rawType() : lType;
    PsiType rRawType = rType instanceof PsiClassType classType ? classType.rawType() : rType;
    boolean assignable = lRawType == null || rRawType == null || TypeConversionUtil.isAssignable(lRawType, rRawType);
    boolean shortType = showShortType(lRawType, rRawType);
    return consumer.consume(redIfNotMatch(lRawType, true, shortType).toString(),
                            requiredRow.toString(),
                            redIfNotMatch(rRawType, assignable, shortType).toString(),
                            foundRow.toString());
  }

  static boolean showShortType(@Nullable PsiType lType, @Nullable PsiType rType) {
    if (Comparing.equal(lType, rType)) return true;

    return lType != null && rType != null &&
           (!lType.getPresentableText().equals(rType.getPresentableText()) ||
            lType.getCanonicalText().equals(rType.getCanonicalText()));
  }

  private static @NotNull String getReasonForIncompatibleTypes(PsiType rType) {
    if (rType instanceof PsiMethodReferenceType referenceType) {
      JavaResolveResult[] results = referenceType.getExpression().multiResolve(false);
      if (results.length > 1) {
        PsiElement element1 = results[0].getElement();
        PsiElement element2 = results[1].getElement();
        if (element1 instanceof PsiMethod && element2 instanceof PsiMethod) {
          String candidate1 = format(element1);
          String candidate2 = format(element2);
          return JavaErrorBundle.message("incompatible.types.reason.ambiguous.method.reference", candidate1, candidate2);
        }
      }
    }
    return "";
  }

  private record TypeData(@NotNull PsiTypeParameter @NotNull [] typeParameters, @NotNull PsiSubstitutor substitutor) {}
  private static @NotNull TypeData typeData(PsiType type) {
    PsiTypeParameter[] parameters;
    PsiSubstitutor substitutor;
    if (type instanceof PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      substitutor = resolveResult.getSubstitutor();
      PsiClass psiClass = resolveResult.getElement();
      parameters = psiClass == null || classType.isRaw() ? PsiTypeParameter.EMPTY_ARRAY : psiClass.getTypeParameters();
    }
    else {
      substitutor = PsiSubstitutor.EMPTY;
      parameters = PsiTypeParameter.EMPTY_ARRAY;
    }
    return new TypeData(parameters, substitutor);
  }

  static @NotNull @NlsSafe HtmlChunk redIfNotMatch(@Nullable PsiType type, boolean matches, boolean shortType) {
    if (type == null) return HtmlChunk.empty();
    String typeText;
    if (shortType || type instanceof PsiCapturedWildcardType) {
      typeText = PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiAnonymousClass
                 ? ANONYMOUS + type.getPresentableText()
                 : type.getPresentableText();
    }
    else {
      typeText = type.getCanonicalText();
    }
    Color color = matches
                  ? NewUI.isEnabled() ? JBUI.CurrentTheme.Editor.Tooltip.FOREGROUND : UIUtil.getToolTipForeground()
                  : NamedColorUtil.getErrorForeground();
    return HtmlChunk.tag("font").attr("color", ColorUtil.toHtmlColor(color)).addText(typeText);
  }


  static HighlightInfo.Builder checkReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull JavaResolveResult result) {
    PsiElement refName = ref.getReferenceNameElement();
    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return null;
    PsiElement resolved = result.getElement();

    PsiElement refParent = ref.getParent();

    if (refParent instanceof PsiMethodCallExpression || resolved == null) {
      // reported elsewhere
      return null;
    }

    boolean skipValidityChecks =
      PsiUtil.isInsideJavadocComment(ref) ||
      PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class, true) != null ||
      resolved instanceof PsiPackage && ref.getParent() instanceof PsiJavaCodeReferenceElement;

    final ErrorWithFixes moduleProblem = checkModuleAccess(resolved, ref, result);
    if (!skipValidityChecks && !(result.isValidResult() && moduleProblem == null)) {
      if (moduleProblem != null) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(findPackagePrefix(ref))
          .descriptionAndTooltip(moduleProblem.message);
        moduleProblem.fixes.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
        return info;
      }

      if (!result.isAccessible()) {
        @Nls String description = accessProblemDescription(ref, resolved, result);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
        if (result.isStaticsScopeCorrect() && resolved instanceof PsiJvmMember) {
          HighlightFixUtil.registerAccessQuickFixAction(asConsumer(info), (PsiJvmMember)resolved, ref, result.getCurrentFileResolveScope());
          if (ref instanceof PsiReferenceExpression expression) {
            IntentionAction action = getFixFactory().createRenameWrongRefFix(expression);
            info.registerFix(action, null, null, null, null);
          }
        }
        UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(ref, info);
        return info;
      }

      if (!result.isStaticsScopeCorrect()) {
        String description = staticContextProblemDescription(resolved);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
        HighlightFixUtil.registerStaticProblemQuickFixAction(asConsumer(info), resolved, ref);
        if (ref instanceof PsiReferenceExpression expression) {
          IntentionAction action = getFixFactory().createRenameWrongRefFix(expression);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    return null;
  }

  /**
   * Checks if the specified element is possibly a reference to a static member of a class,
   * when the {@code new} keyword is removed.
   * The element is split into two parts: the qualifier and the reference element.
   * If they both exist and the qualifier references a class and the reference element text matches either
   * the name of a static field or the name of a static method of the class
   * then the method returns true
   *
   * @param element an element to examine
   * @return true if the new expression can actually be a call to a class member (field or method), false otherwise.
   */
  @Contract(value = "null -> false", pure = true)
  static boolean isCallToStaticMember(@Nullable PsiElement element) {
    if (!(element instanceof PsiNewExpression newExpression)) return false;

    PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
    if (reference == null) return false;

    PsiElement qualifier = reference.getQualifier();
    PsiElement memberName = reference.getReferenceNameElement();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement referenceElement) || memberName == null) return false;

    PsiClass clazz = tryCast(referenceElement.resolve(), PsiClass.class);
    if (clazz == null) return false;

    if (newExpression.getArgumentList() == null) {
      PsiField field = clazz.findFieldByName(memberName.getText(), true);
      return field != null && field.hasModifierProperty(PsiModifier.STATIC);
    }
    PsiMethod[] methods = clazz.findMethodsByName(memberName.getText(), true);
    for (PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        if (!containingClass.isInterface() || containingClass == clazz) {
          // a static method in an interface is not resolvable from its subclasses
          return true;
        }
      }
    }
    return false;
  }

  private static @NotNull PsiElement findPackagePrefix(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement candidate = ref;
    while (candidate instanceof PsiJavaCodeReferenceElement element) {
      if (element.resolve() instanceof PsiPackage) return candidate;
      candidate = element.getQualifier();
    }
    return ref;
  }

   private static @NlsSafe @NotNull String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) return formatClass(psiClass);
    if (element instanceof PsiMethod psiMethod) return JavaHighlightUtil.formatMethod(psiMethod);
    if (element instanceof PsiField psiField) return formatField(psiField);
    if (element instanceof PsiLabeledStatement statement) return statement.getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }
}
