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
import com.intellij.codeInsight.daemon.impl.quickfix.QualifyWithThisFix;
import com.intellij.codeInsight.highlighting.HighlightUsagesDescriptionLocation;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.NewUI;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {
  private static final Logger LOG = Logger.getInstance(HighlightUtil.class);

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
    HighlightInfo.Builder highlightInfo = createIncompatibleTypeHighlightInfo(lType, rType, textRange, 0);
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

  static void registerReturnTypeFixes(@NotNull HighlightInfo.Builder info, @NotNull PsiMethod method, @NotNull PsiType expectedReturnType) {
    IntentionAction action = getFixFactory().createMethodReturnFix(method, expectedReturnType, true, true);
    info.registerFix(action, null, null, null, null);
  }

  public static @NotNull @NlsContexts.DetailedDescription String getUnhandledExceptionsDescriptor(@NotNull Collection<? extends PsiClassType> unhandled) {
    return JavaErrorBundle.message("unhandled.exceptions", formatTypes(unhandled), unhandled.size());
  }

  private static @NotNull String formatTypes(@NotNull Collection<? extends PsiClassType> unhandled) {
    return StringUtil.join(unhandled, JavaHighlightUtil::formatType, ", ");
  }

  public static HighlightInfo.Builder checkVariableAlreadyDefined(@NotNull PsiVariable variable) {
    if (variable instanceof ExternallyDefinedPsiElement || variable.isUnnamed()) return null;
    PsiVariable oldVariable = null;
    PsiElement declarationScope = null;
    if (variable instanceof PsiLocalVariable || variable instanceof PsiPatternVariable ||
        variable instanceof PsiParameter parameter &&
        ((declarationScope = parameter.getDeclarationScope()) instanceof PsiCatchSection ||
         declarationScope instanceof PsiForeachStatement ||
         declarationScope instanceof PsiLambdaExpression)) {
      PsiElement scope =
        PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class, PsiResourceList.class);
      VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          return PsiUtil.isJvmLocalVariable(var) && super.check(var, state);
        }
      };
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      if (scope instanceof PsiResourceList && proc.size() == 0) {
        scope = PsiTreeUtil.getParentOfType(variable, PsiFile.class, PsiMethod.class, PsiClassInitializer.class);
        PsiScopesUtil.treeWalkUp(proc, identifier, scope);
      }
      if (proc.size() > 0) {
        oldVariable = proc.getResult(0);
      }
      else if (declarationScope instanceof PsiLambdaExpression) {
        oldVariable = findSameNameSibling(variable);
      }
      else if (variable instanceof PsiPatternVariable patternVariable) {
        oldVariable = findSamePatternVariableInBranches(patternVariable);
      }
    }
    else if (variable instanceof PsiField field) {
      PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      PsiField fieldByName = aClass.findFieldByName(variable.getName(), false);
      if (fieldByName != null && fieldByName != field) {
        oldVariable = fieldByName;
      }
      else {
        oldVariable = ContainerUtil.find(aClass.getRecordComponents(), c -> field.getName().equals(c.getName()));
      }
    }
    else {
      oldVariable = findSameNameSibling(variable);
    }

    if (oldVariable != null) {
      String description = JavaErrorBundle.message("variable.already.defined", variable.getName());
      PsiIdentifier identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      VirtualFile vFile = PsiUtilCore.getVirtualFile(identifier);
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier);
      if (vFile != null) {
        String path = FileUtil.toSystemIndependentName(vFile.getPath());
        String linkText = "<a href=\"#navigation/" + path + ":" + oldVariable.getTextOffset() + "\">" + variable.getName() + "</a>";
        builder = builder.description(description)
          .escapedToolTip("<html>" + JavaErrorBundle.message("variable.already.defined", linkText) + "</html>");
      }
      else {
        builder = builder.descriptionAndTooltip(description);
      }
      IntentionAction action1 = getFixFactory().createNavigateToAlreadyDeclaredVariableFix(oldVariable);
      builder.registerFix(action1, null, null, null, null);
      if (variable instanceof PsiLocalVariable localVariable) {
        IntentionAction action = getFixFactory().createReuseVariableDeclarationFix(localVariable);
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  private static PsiPatternVariable findSamePatternVariableInBranches(@NotNull PsiPatternVariable variable) {
    PsiPattern pattern = variable.getPattern();
    PatternResolveState hint = PatternResolveState.WHEN_TRUE;
    VariablesNotProcessor proc = new VariablesNotProcessor(variable, false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        return var instanceof PsiPatternVariable && super.check(var, state);
      }
    };
    PsiElement lastParent = pattern;
    for (PsiElement parent = lastParent.getParent(); parent != null; lastParent = parent, parent = parent.getParent()) {
      if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiPrefixExpression expression && expression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
        hint = hint.invert();
        continue;
      }
      if (parent instanceof PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          PatternResolveState targetHint = PatternResolveState.fromBoolean(tokenType.equals(JavaTokenType.OROR));
          if (hint == targetHint) {
            for (PsiExpression operand : expression.getOperands()) {
              if (operand == lastParent) break;
              operand.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
            }
          }
          continue;
        }
      }
      if (parent instanceof PsiConditionalExpression conditional) {
        PsiExpression thenExpression = conditional.getThenExpression();
        if (lastParent == thenExpression) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_FALSE.putInto(ResolveState.initial()), null, pattern);
        }
        else if (lastParent == conditional.getElseExpression()) {
          conditional.getCondition()
            .processDeclarations(proc, PatternResolveState.WHEN_TRUE.putInto(ResolveState.initial()), null, pattern);
          if (thenExpression != null) {
            thenExpression.processDeclarations(proc, hint.putInto(ResolveState.initial()), null, pattern);
          }
        }
      }
      break;
    }
    return proc.size() > 0 ? (PsiPatternVariable)proc.getResult(0) : null;
  }

  private static PsiVariable findSameNameSibling(@NotNull PsiVariable variable) {
    PsiElement scope = variable.getParent();
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiVariable psiVariable) {
        if (child.equals(variable)) continue;
        if (Objects.equals(variable.getName(), psiVariable.getName())) {
          return psiVariable;
        }
      }
    }
    return null;
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

  static void checkSwitchExpressionReturnTypeCompatible(@NotNull PsiSwitchExpression switchExpression,
                                                        @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!PsiPolyExpressionUtil.isPolyExpression(switchExpression)) {
      return;
    }
    PsiType switchExpressionType = switchExpression.getType();
    if (switchExpressionType != null) {
      for (PsiExpression expression : PsiUtil.getSwitchResultExpressions(switchExpression)) {
        PsiType expressionType = expression.getType();
        if (expressionType != null && !TypeConversionUtil.areTypesAssignmentCompatible(switchExpressionType, expression)) {
          String text = JavaErrorBundle
            .message("bad.type.in.switch.expression", expressionType.getCanonicalText(), switchExpressionType.getCanonicalText());
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
          HighlightFixUtil.registerIncompatibleTypeFixes(asConsumer(info), switchExpression, switchExpressionType, expressionType);
          errorSink.accept(info);
        }
      }

      if (PsiTypes.voidType().equals(switchExpressionType)) {
        String text = JavaErrorBundle.message("switch.expression.cannot.be.void");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(switchExpression.getFirstChild()).descriptionAndTooltip(text);
        errorSink.accept(info);
      }
    }
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

  static HighlightInfo.Builder checkResourceVariableIsFinal(@NotNull PsiResourceExpression resource) {
    PsiExpression expression = resource.getExpression();

    if (expression instanceof PsiThisExpression) return null;

    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target == null) return null;

      if (target instanceof PsiVariable variable) {
        PsiModifierList modifierList = variable.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) return null;

        if (!(variable instanceof PsiField) && HighlightControlFlowUtil.isEffectivelyFinal(variable, resource, ref)) {
          return null;
        }
      }

      String text = JavaErrorBundle.message("resource.variable.must.be.final");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
    }

    String text = JavaErrorBundle.message("declaration.or.variable.expected");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(text);
  }


  static void checkSwitchExpressionHasResult(@NotNull PsiSwitchExpression switchExpression,
                                             @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiCodeBlock switchBody = switchExpression.getBody();
    if (switchBody != null) {
      PsiStatement lastStatement = PsiTreeUtil.getPrevSiblingOfType(switchBody.getRBrace(), PsiStatement.class);
      boolean hasResult = false;
      if (lastStatement instanceof PsiSwitchLabeledRuleStatement rule) {
        boolean reported = false;
        for (;
             rule != null;
             rule = PsiTreeUtil.getPrevSiblingOfType(rule, PsiSwitchLabeledRuleStatement.class)) {
          PsiStatement ruleBody = rule.getBody();
          if (ruleBody instanceof PsiExpressionStatement) {
            hasResult = true;
          }
          // the expression and throw statements are fine, only the block statement could be an issue
          // 15.28.1 If the switch block consists of switch rules, then any switch rule block cannot complete normally
          if (ruleBody instanceof PsiBlockStatement) {
            if (ControlFlowUtils.statementMayCompleteNormally(ruleBody)) {
              PsiElement target = ObjectUtils.notNull(tryCast(rule.getFirstChild(), PsiKeyword.class), rule);
              String message = JavaErrorBundle.message("switch.expr.rule.should.produce.result");
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message);
              errorSink.accept(info);
              reported = true;
            }
            else if (!hasResult && hasYield(switchExpression, ruleBody)) {
              hasResult = true;
            }
          }
        }
        if (reported) {
          return;
        }
      }
      else {
        // previous statements may have no result as well, but in that case they fall through to the last one, which needs to be checked anyway
        if (lastStatement != null && ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
          PsiElement target =
            ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
          String message = JavaErrorBundle.message("switch.expr.should.produce.result");
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target).descriptionAndTooltip(message);
          errorSink.accept(info);
          return;
        }
        hasResult = hasYield(switchExpression, switchBody);
      }
      if (!hasResult) {
        PsiElement target = ObjectUtils.notNull(tryCast(switchExpression.getFirstChild(), PsiKeyword.class), switchExpression);
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(target)
          .descriptionAndTooltip(JavaErrorBundle.message("switch.expr.no.result"));
        errorSink.accept(info);
      }
    }
  }

  private static boolean hasYield(@NotNull PsiSwitchExpression switchExpression, @NotNull PsiElement scope) {
    class YieldFinder extends JavaRecursiveElementWalkingVisitor {
      private boolean hasYield;

      @Override
      public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
        if (statement.findEnclosingExpression() == switchExpression) {
          hasYield = true;
          stopWalking();
        }
      }

      // do not go inside to save time: declarations cannot contain yield that points to outer switch expression
      @Override
      public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {}

      // do not go inside to save time: expressions cannot contain yield that points to outer switch expression
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {}
    }
    YieldFinder finder = new YieldFinder();
    scope.accept(finder);
    return finder.hasYield;
  }


  static HighlightInfo.Builder checkMemberReferencedBeforeConstructorCalled(@NotNull PsiElement expression,
                                                                            @Nullable PsiElement resolved,
                                                                            @NotNull Function<? super PsiElement, ? extends PsiMethod> surroundingConstructor) {
    PsiMethod constructor = surroundingConstructor.apply(expression);
    if (constructor == null) {
      // not inside expression inside constructor
      return null;
    }
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
    if (constructorCall == null) {
      return null;
    }
    if (expression.getTextOffset() > constructorCall.getTextOffset() + constructorCall.getTextLength()) {
      return null;
    }
    // is in or before this() or super() call

    PsiClass referencedClass;
    String resolvedName;
    PsiElement parent = expression.getParent();
    if (expression instanceof PsiJavaCodeReferenceElement referenceElement) {
      // redirected ctr
      if (PsiKeyword.THIS.equals(referenceElement.getReferenceName())
          && resolved instanceof PsiMethod psiMethod
          && psiMethod.isConstructor()) {
        return null;
      }
      PsiElement qualifier = referenceElement.getQualifier();
      referencedClass = PsiUtil.resolveClassInType(qualifier instanceof PsiExpression psiExpression ? psiExpression.getType() : null);

      boolean isSuperCall = JavaPsiConstructorUtil.isSuperConstructorCall(parent);
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression referenceExpression) {
          resolved = referenceExpression.resolve();
          expression = qualifier;
          referencedClass = PsiUtil.resolveClassInType(referenceExpression.getType());
        }
        else if (qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          if (resolved instanceof PsiMethod psiMethod) {
            referencedClass = psiMethod.getContainingClass();
          }
        }
        else if (qualifier instanceof PsiThisExpression thisExpression) {
          referencedClass = PsiUtil.resolveClassInType(thisExpression.getType());
        }
      }
      if (resolved instanceof PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return null;
        LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
        if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) &&
            languageLevel != LanguageLevel.JDK_22_PREVIEW &&
            isOnSimpleAssignmentLeftHand(expression) &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
          if (field.hasInitializer()) {
            String fieldName = PsiFormatUtil.formatVariable(
              field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
            String description = JavaErrorBundle.message("assign.initialized.field.before.constructor.call", fieldName);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
          }
          return null;
        }
        resolvedName =
          PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = field.getContainingClass();
      }
      else if (resolved instanceof PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
        PsiElement nameElement =
          expression instanceof PsiThisExpression ? expression : ((PsiJavaCodeReferenceElement)expression).getReferenceNameElement();
        String name = nameElement == null ? null : nameElement.getText();
        if (isSuperCall) {
          if (referencedClass == null) return null;
          if (qualifier == null) {
            PsiClass superClass = referencedClass.getSuperClass();
            if (superClass != null
                && PsiUtil.isInnerClass(superClass)
                && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
              // by default super() is considered "this"-qualified
              resolvedName = PsiKeyword.THIS;
            }
            else {
              return null;
            }
          }
          else {
            resolvedName = qualifier.getText();
          }
        }
        else if (PsiKeyword.THIS.equals(name)) {
          resolvedName = PsiKeyword.THIS;
        }
        else {
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                    PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, 0);
          if (referencedClass == null) referencedClass = method.getContainingClass();
        }
      }
      else if (resolved instanceof PsiClass aClass) {
        if (expression instanceof PsiReferenceExpression) return null;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return null;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
      }
      else {
        return null;
      }
    }
    else if (expression instanceof PsiThisExpression  || expression instanceof PsiSuperExpression) {
      PsiQualifiedExpression qualifiedExpression = (PsiQualifiedExpression)expression;
      referencedClass = PsiUtil.resolveClassInType(qualifiedExpression.getType());
      String keyword = expression instanceof PsiThisExpression ? PsiKeyword.THIS : PsiKeyword.SUPER;
      PsiJavaCodeReferenceElement qualifier = qualifiedExpression.getQualifier();
      resolvedName = qualifier != null && qualifier.resolve() instanceof PsiClass aClass
                     ? PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME) + "." + keyword
                     : keyword;
    }
    else {
      return null;
    }

    if (referencedClass == null ||
        PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class, true, PsiExpression.class) != null) {
      return null;
    }

    PsiClass parentClass = constructor.getContainingClass();
    if (parentClass == null) {
      return null;
    }

    // references to private methods from the outer class are not calls to super methods
    // even if the outer class is the superclass
    if (resolved instanceof PsiMember member && member.hasModifierProperty(PsiModifier.PRIVATE) && referencedClass != parentClass) {
      return null;
    }
    // field or method should be declared in this class or super
    if (!InheritanceUtil.isInheritorOrSelf(parentClass, referencedClass, true)) return null;
    // and point to our instance
    if (expression instanceof PsiReferenceExpression ref) {
      PsiExpression qualifier = ref.getQualifierExpression();
      if (!isThisOrSuperReference(qualifier, parentClass)) {
        return null;
      }
      else if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        if (((PsiQualifiedExpression)qualifier).getQualifier() != null) return null;
      }
    }

    if (expression instanceof PsiThisExpression && referencedClass != parentClass) return null;

    if (expression instanceof PsiJavaCodeReferenceElement) {
      if (!parentClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class)) &&
          PsiTreeUtil.getParentOfType(expression, PsiTypeElement.class) != null) {
        return null;
      }

      if (PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) {
        return null;
      }

      if (parent instanceof PsiNewExpression newExpression &&
          newExpression.isArrayCreation() &&
          newExpression.getClassOrAnonymousClassReference() == expression) {
        return null;
      }
      if (parent instanceof PsiThisExpression || parent instanceof PsiSuperExpression) return null;
    }
    if (!(expression instanceof PsiThisExpression) && !(expression instanceof PsiSuperExpression) ||
        ((PsiQualifiedExpression)expression).getQualifier() == null) {
      PsiClass expressionClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class, true);
      while (expressionClass != null && parentClass != expressionClass) {
        if (InheritanceUtil.isInheritorOrSelf(expressionClass, referencedClass, true)) {
          return null;
        }
        expressionClass = PsiTreeUtil.getParentOfType(expressionClass, PsiClass.class, true);
      }
    }

    if (expression instanceof PsiThisExpression) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
      if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) && languageLevel != LanguageLevel.JDK_22_PREVIEW) {
        parent = PsiUtil.skipParenthesizedExprUp(parent);
        if (isOnSimpleAssignmentLeftHand(parent) &&
            parent instanceof PsiReferenceExpression ref &&
            ref.resolve() instanceof PsiField field &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
          return null;
        }
      }
    }
    HighlightInfo.Builder builder = createMemberReferencedError(resolvedName, expression.getTextRange(), resolved instanceof PsiMethod);
    if (expression instanceof PsiReferenceExpression ref && PsiUtil.isInnerClass(parentClass)) {
      String referenceName = ref.getReferenceName();
      PsiClass containingClass = parentClass.getContainingClass();
      LOG.assertTrue(containingClass != null);
      PsiField fieldInContainingClass = containingClass.findFieldByName(referenceName, true);
      if (fieldInContainingClass != null && ref.getQualifierExpression() == null) {
        builder.registerFix(new QualifyWithThisFix(containingClass, ref), null, null, null, null);
      }
    }

    return builder;
  }

  private static boolean isOnSimpleAssignmentLeftHand(@NotNull PsiElement expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    return parent instanceof PsiAssignmentExpression assignment &&
           JavaTokenType.EQ == assignment.getOperationTokenType() &&
           PsiTreeUtil.isAncestor(assignment.getLExpression(), expr, false);
  }

  private static @NotNull HighlightInfo.Builder createMemberReferencedError(@NotNull String resolvedName, @NotNull TextRange textRange, boolean methodCall) {
    String description = methodCall
                         ? JavaErrorBundle.message("method.called.before.constructor.called", resolvedName)
                         : JavaErrorBundle.message("member.referenced.before.constructor.called", resolvedName);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
  }

  private static boolean isThisOrSuperReference(@Nullable PsiExpression qualifierExpression, @NotNull PsiClass aClass) {
    if (qualifierExpression == null) return true;
    if (!(qualifierExpression instanceof PsiQualifiedExpression expression)) return false;
    PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
  }


  static HighlightInfo.Builder checkConditionalExpressionBranchTypesMatch(@NotNull PsiExpression expression, @Nullable PsiType type) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiConditionalExpression conditionalExpression)) {
      return null;
    }
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) return null;
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null || type == null) return null;
    if (conditionalExpression.getType() == null) {
      if (PsiUtil.isLanguageLevel8OrHigher(conditionalExpression) && PsiPolyExpressionUtil.isPolyExpression(conditionalExpression)) {
        return null;
      }
      // cannot derive type of conditional expression
      // elseType will never be cast-able to thenType, so no quick fix here
      return createIncompatibleTypeHighlightInfo(thenType, type, expression.getTextRange(), 0);
    }
    return null;
  }

  static @NotNull HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                                            @Nullable PsiType rType,
                                                                            @NotNull TextRange textRange,
                                                                            int navigationShift) {
    return createIncompatibleTypeHighlightInfo(lType, rType, textRange, navigationShift, getReasonForIncompatibleTypes(rType));
  }

  static @NotNull HighlightInfo.Builder createIncompatibleTypeHighlightInfo(@NotNull PsiType lType,
                                                                            @Nullable PsiType rType,
                                                                            @NotNull TextRange textRange,
                                                                            int navigationShift,
                                                                            @NotNull String reason) {
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
      .navigationShift(navigationShift);
  }

  static HighlightInfo.Builder checkExtraSemicolonBetweenImportStatements(@NotNull PsiJavaToken token,
                                                                          IElementType type,
                                                                          @NotNull LanguageLevel level) {
    if (type == JavaTokenType.SEMICOLON && level.isAtLeast(LanguageLevel.JDK_21) && PsiUtil.isFollowedByImport(token)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(token)
        .registerFix(QuickFixFactory.getInstance().createDeleteFix(token), null, null, null, null)
        .descriptionAndTooltip(JavaErrorBundle.message("error.extra.semicolons.between.import.statements.not.allowed"));
    }
    return null;
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


  static HighlightInfo.Builder checkReference(@NotNull PsiJavaCodeReferenceElement ref,
                                              @NotNull JavaResolveResult result,
                                              @NotNull PsiFile containingFile,
                                              @NotNull LanguageLevel languageLevel) {
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

    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      return HighlightControlFlowUtil.checkVariableMustBeFinal((PsiVariable)resolved, ref, languageLevel);
    }

    if (resolved instanceof PsiClass psiClass &&
        psiClass.getContainingClass() == null &&
        PsiUtil.isFromDefaultPackage(resolved) &&
        (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null ||
         PsiUtil.isModuleFile(containingFile) ||
         !PsiUtil.isFromDefaultPackage(containingFile))) {
      String description = JavaErrorBundle.message("class.in.default.package", psiClass.getName());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refName).descriptionAndTooltip(description);
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

  static @NlsSafe @NotNull String format(@NotNull PsiElement element) {
    if (element instanceof PsiClass psiClass) return formatClass(psiClass);
    if (element instanceof PsiMethod psiMethod) return JavaHighlightUtil.formatMethod(psiMethod);
    if (element instanceof PsiField psiField) return formatField(psiField);
    if (element instanceof PsiLabeledStatement statement) return statement.getName() + ':';
    return ElementDescriptionUtil.getElementDescription(element, HighlightUsagesDescriptionLocation.INSTANCE);
  }

  private static @NotNull PsiJavaCodeReferenceElement getOuterReferenceParent(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiJavaCodeReferenceElement element = ref;
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        element = (PsiJavaCodeReferenceElement)parent;
      }
      else {
        break;
      }
    }
    return element;
  }

  static HighlightInfo.Builder checkPackageAndClassConflict(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiFile containingFile) {
    if (ref.isQualified() && getOuterReferenceParent(ref).getParent() instanceof PsiPackageStatement) {
      Module module = ModuleUtilCore.findModuleForFile(containingFile);
      if (module != null) {
        GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(ref.getCanonicalText(), scope);
        if (aClass != null) {
          String message = JavaErrorBundle.message("package.clashes.with.class", ref.getText());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(message);
        }
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkElementInReferenceList(@NotNull PsiJavaCodeReferenceElement ref,
                                                           @NotNull PsiReferenceList referenceList,
                                                           @NotNull JavaResolveResult resolveResult) {
    PsiElement resolved = resolveResult.getElement();
    HighlightInfo.Builder builder = null;
    PsiElement refGrandParent = referenceList.getParent();
    if (resolved instanceof PsiClass aClass) {
      if (refGrandParent instanceof PsiClass parentClass) {
        if (!(refGrandParent instanceof PsiTypeParameter)) {
          if (referenceList.equals(parentClass.getImplementsList()) || referenceList.equals(parentClass.getExtendsList())) {
            builder = HighlightClassUtil.checkExtendsSealedClass(parentClass, aClass, ref);
          }
        }
      }
    }
    return builder;
  }

  static HighlightInfo.Builder checkClassReferenceAfterQualifier(@NotNull PsiReferenceExpression expression, @Nullable PsiElement resolved) {
    if (!(resolved instanceof PsiClass)) return null;
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return null;
    if (qualifier instanceof PsiReferenceExpression qExpression) {
      PsiElement qualifierResolved = qExpression.resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return null;

      if (qualifierResolved == null) {
        while (true) {
          PsiElement qResolve = qExpression.resolve();
          if (qResolve == null || qResolve instanceof PsiClass || qResolve instanceof PsiPackage) {
            PsiExpression qualifierExpression = qExpression.getQualifierExpression();
            if (qualifierExpression == null) return null;
            if (qualifierExpression instanceof PsiReferenceExpression) {
              qExpression = (PsiReferenceExpression)qualifierExpression;
              continue;
            }
          }
          break;
        }
      }
    }
    String description = JavaErrorBundle.message("expected.class.or.package");
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(qualifier).descriptionAndTooltip(description);
    IntentionAction action = getFixFactory().createRemoveQualifierFix(qualifier, expression, (PsiClass)resolved);
    info.registerFix(action, null, null, null, null);
    return info;
  }

  private static @NotNull LanguageLevel getApplicableLevel(@NotNull PsiFile file, @NotNull JavaFeature feature) {
    LanguageLevel standardLevel = feature.getStandardLevel();
    LanguageLevel featureLevel = feature.getMinimumLevel();
    if (featureLevel.isPreview()) {
      JavaSdkVersion sdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(file);
      if (sdkVersion != null) {
        if (standardLevel != null && sdkVersion.isAtLeast(JavaSdkVersion.fromLanguageLevel(standardLevel))) {
          return standardLevel;
        }
        LanguageLevel previewLevel = sdkVersion.getMaxLanguageLevel().getPreviewLevel();
        if (previewLevel != null && previewLevel.isAtLeast(featureLevel)) {
          return previewLevel;
        }
      }
    }
    return featureLevel;
  }

  static @Nullable HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                                      @NotNull JavaFeature feature,
                                                      @NotNull LanguageLevel level,
                                                      @NotNull PsiFile file) {
    return checkFeature(element, feature, level, file, null, HighlightInfoType.ERROR);
  }

  static @Nullable HighlightInfo.Builder checkFeature(@NotNull PsiElement element,
                                                      @NotNull JavaFeature feature,
                                                      @NotNull LanguageLevel level,
                                                      @NotNull PsiFile file,
                                                      @Nullable @NlsContexts.DetailedDescription String message,
                                                      @NotNull HighlightInfoType highlightInfoType) {
    if (!feature.isSufficient(level)) {
      message = message == null ? getUnsupportedFeatureMessage(feature, level, file) : message;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(highlightInfoType).range(element).descriptionAndTooltip(message);
      registerIncreaseLanguageLevelFixes(file, feature, info);
      return info;
    }

    return null;
  }

  public static void registerIncreaseLanguageLevelFixes(@NotNull PsiElement element,
                                                        @NotNull JavaFeature feature,
                                                        HighlightInfo.Builder info) {
    if (info == null) return;
    for (CommonIntentionAction action : getIncreaseLanguageLevelFixes(element, feature)) {
      info.registerFix(action.asIntention(), null, null, null, null);
    }
  }

  public static @NotNull List<CommonIntentionAction> getIncreaseLanguageLevelFixes(
    @NotNull PsiElement element, @NotNull JavaFeature feature) {
    if (PsiUtil.isAvailable(feature, element)) return List.of();
    if (feature.isLimited()) return List.of(); //no reason for applying it because it can be outdated
    LanguageLevel applicableLevel = getApplicableLevel(element.getContainingFile(), feature);
    if (applicableLevel == LanguageLevel.JDK_X) return List.of(); // do not suggest to use experimental level
    return List.of(getFixFactory().createIncreaseLanguageLevelFix(applicableLevel),
                   getFixFactory().createUpgradeSdkFor(applicableLevel),
                   getFixFactory().createShowModulePropertiesFix(element));
  }

  private static @NotNull @NlsContexts.DetailedDescription String getUnsupportedFeatureMessage(@NotNull JavaFeature feature,
                                                                                               @NotNull LanguageLevel level,
                                                                                               @NotNull PsiFile file) {
    String name = feature.getFeatureName();
    String version = JavaSdkVersion.fromLanguageLevel(level).getDescription();
    String message = JavaErrorBundle.message("insufficient.language.level", name, version);

    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      LanguageLevel moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
      if (moduleLanguageLevel.isAtLeast(feature.getMinimumLevel()) && !feature.isLimited()) {
        for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
          if (pusher instanceof JavaLanguageLevelPusher javaPusher) {
            String newMessage = javaPusher.getInconsistencyLanguageLevelMessage(message, level, file);
            if (newMessage != null) {
              return newMessage;
            }
          }
        }
      }
    }

    return message;
  }
}
