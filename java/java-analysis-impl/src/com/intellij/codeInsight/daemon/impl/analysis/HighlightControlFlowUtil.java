// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.ChangeModifierRequest;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.BitUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class HighlightControlFlowUtil {

  private static QuickFixFactory getQuickFixFactory() {
    return QuickFixFactory.getInstance();
  }

  private HighlightControlFlowUtil() { }

  static HighlightInfo.Builder checkMissingReturnStatement(@Nullable PsiCodeBlock body, @Nullable PsiType returnType) {
    if (body == null || returnType == null || PsiTypes.voidType().equals(returnType.getDeepComponentType())) {
      return null;
    }

    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
      if (!ControlFlowUtil.returnPresent(controlFlow)) {
        PsiJavaToken rBrace = body.getRBrace();
        PsiElement context = rBrace == null ? body.getLastChild() : rBrace;
        String message = JavaErrorBundle.message("missing.return.statement");
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(message);
        PsiElement parent = body.getParent();
        if (parent instanceof PsiMethod method) {
          IntentionAction action1 = getQuickFixFactory().createAddReturnFix(method);
          info.registerFix(action1, null, null, null, null);
          IntentionAction action = getQuickFixFactory().createMethodReturnFix(method, PsiTypes.voidType(), true);
          info.registerFix(action, null, null, null, null);
        }
        if (parent instanceof PsiLambdaExpression lambda) {
          IntentionAction action = getQuickFixFactory().createAddReturnFix(lambda);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }
    catch (AnalysisCanceledException ignored) { }

    return null;
  }

  public static @NotNull ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getControlFlow(body, policy, ControlFlowOptions.NO_CONST_EVALUATE);
  }

  private static @NotNull ControlFlow getControlFlow(@NotNull PsiElement context) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getControlFlow(context, policy, ControlFlowOptions.create(true, true, true));
  }

  static HighlightInfo.Builder checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
    if (codeBlock == null) return null;
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      AllVariablesControlFlowPolicy policy = AllVariablesControlFlowPolicy.getInstance();
      ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock, policy, ControlFlowOptions.NO_CONST_EVALUATE);
      PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
      if (unreachableStatement != null) {
        if (unreachableStatement instanceof PsiCodeBlock && unreachableStatement.getParent() instanceof PsiBlockStatement) {
          unreachableStatement = unreachableStatement.getParent();
        }
        if (unreachableStatement instanceof PsiStatement) {
          PsiElement parent = unreachableStatement.getParent();
          if (parent instanceof PsiWhileStatement || parent instanceof PsiForStatement) {
            PsiExpression condition = ((PsiConditionalLoopStatement)parent).getCondition();
            if (Boolean.FALSE.equals(ExpressionUtils.computeConstantExpression(condition))) {
              HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(condition)
                .descriptionAndTooltip(JavaErrorBundle.message("unreachable.statement.false.condition"));
              IntentionAction action = getQuickFixFactory().createSimplifyBooleanFix(condition, false);
              info.registerFix(action, null, null, null, null);
              return info;
            }
          }
        }
        String description = JavaErrorBundle.message("unreachable.statement");
        PsiElement keyword = null;
        if (unreachableStatement instanceof PsiIfStatement ||
            unreachableStatement instanceof PsiSwitchBlock ||
            unreachableStatement instanceof PsiLoopStatement ||
            unreachableStatement instanceof PsiThrowStatement ||
            unreachableStatement instanceof PsiReturnStatement ||
            unreachableStatement instanceof PsiYieldStatement ||
            unreachableStatement instanceof PsiTryStatement ||
            unreachableStatement instanceof PsiSynchronizedStatement ||
            unreachableStatement instanceof PsiAssertStatement ||
            unreachableStatement instanceof PsiLabeledStatement) {
          keyword = unreachableStatement.getFirstChild();
        }
        PsiElement element = keyword != null ? keyword : unreachableStatement;
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
        IntentionAction action =
          getQuickFixFactory().createDeleteFix(unreachableStatement, QuickFixBundle.message("delete.unreachable.statement.fix.text"));
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    catch (AnalysisCanceledException | IndexNotReadyException e) {
      // incomplete code
    }
    return null;
  }

  public static boolean isFieldInitializedAfterObjectConstruction(@NotNull PsiField field) {
    if (field.hasInitializer()) return true;
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      // field might be assigned in the other field initializers
      if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic, Predicates.alwaysTrue())) return true;
    }
    PsiClassInitializer[] initializers;
    if (aClass != null) {
      initializers = aClass.getInitializers();
    }
    else {
      return false;
    }
    if (isFieldInitializedInClassInitializer(field, isFieldStatic, initializers)) return true;
    if (isFieldStatic) {
      return false;
    }
    else {
      // instance field should be initialized at the end of each constructor
      PsiMethod[] constructors = aClass.getConstructors();

      if (constructors.length == 0) return false;
      nextConstructor:
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock ctrBody = constructor.getBody();
        if (ctrBody == null) return false;
        for (PsiMethod redirectedConstructor : JavaHighlightUtil.getChainedConstructors(constructor)) {
          PsiCodeBlock body = redirectedConstructor.getBody();
          if (body != null && variableDefinitelyAssignedIn(field, body, true)) continue nextConstructor;
        }
        if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody, true)) {
          continue;
        }
        return false;
      }
      return true;
    }
  }

  private static boolean isFieldInitializedInClassInitializer(@NotNull PsiField field,
                                                              boolean isFieldStatic,
                                                              PsiClassInitializer @NotNull [] initializers) {
    return ContainerUtil.find(initializers, initializer -> initializer.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
                                                           && variableDefinitelyAssignedIn(field, initializer.getBody(), true)) != null;
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(@NotNull PsiClass aClass,
                                                                   @NotNull PsiField field,
                                                                   boolean fieldStatic,
                                                                   @NotNull Predicate<? super PsiField> condition) {
    for (PsiField psiField : aClass.getFields()) {
      if (psiField != field
          && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic
          && variableDefinitelyAssignedIn(field, psiField, true)
          && condition.test(psiField)) {
        return true;
      }
    }
    return false;
  }

  static boolean isRecursivelyCalledConstructor(@NotNull PsiMethod constructor) {
    JavaHighlightUtil.ConstructorVisitorInfo info = new JavaHighlightUtil.ConstructorVisitorInfo();
    JavaHighlightUtil.visitConstructorChain(constructor, info);
    if (info.recursivelyCalledConstructor == null) return false;
    // our constructor is reached from some other constructor by constructor chain
    return info.visitedConstructors.indexOf(info.recursivelyCalledConstructor) <= info.visitedConstructors.indexOf(constructor);
  }

  public static boolean isAssigned(@NotNull PsiParameter parameter) {
    ParamWriteProcessor processor = new ParamWriteProcessor();
    ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), true).forEach(processor);
    return processor.isWriteRefFound();
  }

  private static class ParamWriteProcessor implements Processor<PsiReference> {
    private volatile boolean myIsWriteRefFound;
    @Override
    public boolean process(@NotNull PsiReference reference) {
      PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression ref && PsiUtil.isAccessedForWriting(ref)) {
        myIsWriteRefFound = true;
        return false;
      }
      return true;
    }

    private boolean isWriteRefFound() {
      return myIsWriteRefFound;
    }
  }

  /**
   * see JLS chapter 16
   * @return true if variable assigned (maybe more than once)
   */
  public static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    return variableDefinitelyAssignedIn(variable, context, false);
  }

  private static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable,
                                                      @NotNull PsiElement context,
                                                      boolean resultOnIncompleteCode) {
    try {
      return ControlFlowUtil.isVariableDefinitelyAssigned(variable, getControlFlow(context));
    }
    catch (AnalysisCanceledException e) {
      return resultOnIncompleteCode;
    }
  }

  private static boolean variableDefinitelyNotAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    try {
      return ControlFlowUtil.isVariableDefinitelyNotAssigned(variable, getControlFlow(context));
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
  }

  static HighlightInfo.Builder checkRecordComponentInitialized(@NotNull PsiRecordComponent component) {
    PsiClass aClass = component.getContainingClass();
    if (aClass == null) return null;
    PsiIdentifier identifier = component.getNameIdentifier();
    if (identifier == null) return null;
    PsiMethod canonicalConstructor = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
    if (canonicalConstructor == null || canonicalConstructor instanceof LightRecordCanonicalConstructor) return null;
    if (JavaPsiRecordUtil.isCompactConstructor(canonicalConstructor)) return null;
    PsiCodeBlock body = canonicalConstructor.getBody();
    if (body == null) return null;
    PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
    if (field == null || variableDefinitelyAssignedIn(field, body, true)) return null;
    String description = JavaErrorBundle.message("record.component.not.initialized", field.getName());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(description);
  }

  static HighlightInfo.Builder checkFinalFieldInitialized(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    if (isFieldInitializedAfterObjectConstruction(field)) return null;
    if (PsiUtilCore.hasErrorElementChild(field)) return null;
    String description = JavaErrorBundle.message("variable.not.initialized", field.getName());
    TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
    IntentionAction action3 = getQuickFixFactory().createCreateConstructorParameterFromFieldFix(field);
    builder.registerFix(action3, null, null, HighlightMethodUtil.getFixRange(field), null);
    IntentionAction action2 = getQuickFixFactory().createInitializeFinalFieldInConstructorFix(field);
    builder.registerFix(action2, null, null, HighlightMethodUtil.getFixRange(field), null);
    IntentionAction action1 = getQuickFixFactory().createAddVariableInitializerFix(field);
    builder.registerFix(action1, null, null, null, null);
    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !containingClass.isInterface()) {
      IntentionAction action = getQuickFixFactory().createModifierListFix(field, PsiModifier.FINAL, false, false);
      builder.registerFix(action, null, null, null, null);
    }
    return builder;
  }


  public static HighlightInfo.Builder checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                          @NotNull PsiVariable variable,
                                                                          @NotNull Map<? super PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                                          @NotNull PsiFile containingFile) {
    return checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems, containingFile, false);
  }

  public static HighlightInfo.Builder checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                          @NotNull PsiVariable variable,
                                                                          @NotNull Map<? super PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                                          @NotNull PsiFile containingFile,
                                                                          boolean ignoreFinality) {
    if (variable instanceof ImplicitVariable) return null;
    if (!PsiUtil.isAccessedForReading(expression)) return null;
    int startOffset = expression.getTextRange().getStartOffset();
    PsiElement topBlock;
    if (variable.hasInitializer()) {
      topBlock = PsiUtil.getVariableCodeBlock(variable, variable);
      if (topBlock == null) return null;
    }
    else {
      PsiElement scope = variable instanceof PsiField field
                         ? field.getContainingClass()
                         : variable.getParent() != null ? variable.getParent().getParent() : null;
      while (scope instanceof PsiCodeBlock && scope.getParent() instanceof PsiSwitchBlock) {
        scope = PsiTreeUtil.getParentOfType(scope, PsiCodeBlock.class);
      }

      topBlock = FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile ? scope : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
      if (variable instanceof PsiField field) {
        // non-final field already initialized with default value
        if (!ignoreFinality && !variable.hasModifierProperty(PsiModifier.FINAL)) return null;
        // a final field may be initialized in ctor or class initializer only
        // if we're inside non-ctr method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && HighlightUtil.findEnclosingFieldInitializer(expression) == null) {
          return null;
        }
        if (topBlock == null) return null;
        PsiElement parent = topBlock.getParent();
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, field.getContainingClass())) return null;
        PsiCodeBlock block;
        PsiClass aClass;
        if (parent instanceof PsiMethod constructor) {
          if (!containingFile.getManager().areElementsEquivalent(constructor.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          // static variables already initialized in class initializers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return null;
          // as a last chance, field may be initialized in this() call
          for (PsiMethod redirectedConstructor : JavaHighlightUtil.getChainedConstructors(constructor)) {
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            if (JavaPsiRecordUtil.isCompactConstructor(redirectedConstructor)) return null;
            PsiCodeBlock body = redirectedConstructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body, true)) {
              return null;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer classInitializer) {
          if (!containingFile.getManager().areElementsEquivalent(classInitializer.getContainingClass(), field.getContainingClass())) {
            return null;
          }
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();

          if (aClass == null || isFieldInitializedInOtherFieldInitializer(aClass, field, variable.hasModifierProperty(PsiModifier.STATIC),
                                                                          f -> startOffset > f.getTextOffset())) {
            return null;
          }
        }
        else {
          // field reference outside code block
          // check variable initialized before its usage
          aClass = field.getContainingClass();
          PsiField anotherField = PsiTreeUtil.getTopmostParentOfType(expression, PsiField.class);
          if (aClass == null ||
              isFieldInitializedInOtherFieldInitializer(aClass, field, field.hasModifierProperty(PsiModifier.STATIC),
                                                        f -> f != anotherField && startOffset > f.getTextOffset())) {
            return null;
          }
          if (anotherField != null
              && !anotherField.hasModifierProperty(PsiModifier.STATIC)
              && field.hasModifierProperty(PsiModifier.STATIC)
              && isFieldInitializedInClassInitializer(field, true, aClass.getInitializers())) {
            return null;
          }
          if (anotherField != null && anotherField.hasInitializer() && !PsiAugmentProvider.canTrustFieldInitializer(anotherField)) {
            return null;
          }

          int offset = startOffset;
          if (anotherField != null && anotherField.getContainingClass() == aClass && !field.hasModifierProperty(PsiModifier.STATIC)) {
            offset = 0;
          }
          block = null;
          // initializers will be checked later
          for (PsiMethod constructor : aClass.getConstructors()) {
            // variable must be initialized before its usage
            if (offset < constructor.getTextRange().getStartOffset()) continue;
            PsiCodeBlock body = constructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return null;
            }
            // as a last chance, field may be initialized in this() call
            for (PsiMethod redirectedConstructor : JavaHighlightUtil.getChainedConstructors(constructor)) {
              // variable must be initialized before its usage
              if (offset < redirectedConstructor.getTextRange().getStartOffset()) continue;
              PsiCodeBlock redirectedBody = redirectedConstructor.getBody();
              if (redirectedBody != null && variableDefinitelyAssignedIn(variable, redirectedBody)) {
                return null;
              }
            }
          }
        }

        if (aClass != null) {
          // field may be initialized in class initializer
          for (PsiClassInitializer initializer : aClass.getInitializers()) {
            PsiCodeBlock body = initializer.getBody();
            if (body == block) break;
            // variable referenced in initializer must be initialized in initializer preceding assignment
            // variable referenced in field initializer or in class initializer
            boolean shouldCheckInitializerOrder = block == null || block.getParent() instanceof PsiClassInitializer;
            if (shouldCheckInitializerOrder && startOffset < initializer.getTextRange().getStartOffset()) continue;
            if (initializer.hasModifierProperty(PsiModifier.STATIC) == variable.hasModifierProperty(PsiModifier.STATIC)) {
              if (variableDefinitelyAssignedIn(variable, body)) return null;
            }
          }
        }
      }
    }
    if (topBlock == null) return null;
    Collection<PsiReferenceExpression> codeBlockProblems = uninitializedVarProblems.get(topBlock);
    if (codeBlockProblems == null) {
      try {
        ControlFlow controlFlow = getControlFlow(topBlock);
        codeBlockProblems = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
      }
      catch (AnalysisCanceledException | IndexNotReadyException e) {
        codeBlockProblems = Collections.emptyList();
      }
      uninitializedVarProblems.put(topBlock, codeBlockProblems);
    }
    if (codeBlockProblems.contains(expression)) {
      String name = expression.getElement().getText();
      String description = JavaErrorBundle.message("variable.not.initialized", name);
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
      if (!(variable instanceof LightRecordField)) {
        IntentionAction action1 = getQuickFixFactory().createAddVariableInitializerFix(variable);
        builder.registerFix(action1, null, null, null, null);
      }
      if (variable instanceof PsiLocalVariable) {
        IntentionAction action = HighlightFixUtil.createInsertSwitchDefaultFix(variable, topBlock, expression);
        if (action != null) {
          builder.registerFix(action, null, null, null, null);
        }
      }
      if (variable instanceof PsiField field) {
        ChangeModifierRequest request = MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false);
        QuickFixAction.registerQuickFixActions(builder, null, JvmElementActionFactories.createModifierActions(field, request));
      }
      return builder;
    }

    return null;
  }

  private static boolean inInnerClass(@NotNull PsiElement psiElement, @Nullable PsiClass containingClass) {
    for (PsiElement element = psiElement; element != null; element = element.getParent()) {
      if (element instanceof PsiClass aClass) {
        boolean innerClass = !psiElement.getManager().areElementsEquivalent(element, containingClass);
        if (innerClass) {
          if (element instanceof PsiAnonymousClass anonymous) {
            if (PsiTreeUtil.isAncestor(anonymous.getArgumentList(), psiElement, false)) {
              continue;
            }
            return !insideClassInitialization(containingClass, aClass);
          }
          PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLambdaExpression.class);
          return lambdaExpression == null || !insideClassInitialization(containingClass, aClass);
        }
        return false;
      }
    }
    return false;
  }

  private static boolean insideClassInitialization(@Nullable PsiClass containingClass, PsiClass aClass) {
    PsiMember member = aClass;
    while (member != null) {
      if (member.getContainingClass() == containingClass) {
        return member instanceof PsiField ||
               member instanceof PsiMethod method && method.isConstructor() ||
               member instanceof PsiClassInitializer;
      }
      member = PsiTreeUtil.getParentOfType(member, PsiMember.class, true);
    }
    return false;
  }

  public static boolean isReassigned(@NotNull PsiVariable variable,
                                     @NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      if (parent == null) return false;
      PsiElement declarationScope = parent.getParent();
      if (declarationScope == null) return false;
      Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
    }
    if (variable instanceof PsiParameter parameter) {
      return isAssigned(parameter);
    }
    return false;
  }


  public static HighlightInfo.Builder checkFinalVariableMightAlreadyHaveBeenAssignedTo(@NotNull PsiVariable variable,
                                                                                       @NotNull PsiReferenceExpression expression,
                                                                                       @NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return null;

    PsiElement scope = variable instanceof PsiField
                       ? variable.getParent()
                       : variable.getParent() == null ? null : variable.getParent().getParent();
    PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    if (codeBlock == null) return null;
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

    boolean inLoop = false;
    boolean canDefer = false;
    ControlFlowUtil.VariableInfo variableInfo = ContainerUtil.find(codeBlockProblems, vi -> vi.expression == expression);
    if (variableInfo != null) {
      inLoop = variableInfo instanceof InitializedInLoopProblemInfo;
      canDefer = !inLoop;
    }
    else if (!(variable instanceof PsiField field && isFieldInitializedInAnotherMember(field, expression, codeBlock))) {
      return null;
    }

    String description =
      JavaErrorBundle.message(inLoop ? "variable.assigned.in.loop" : "variable.already.assigned", variable.getName());
    HighlightInfo.Builder highlightInfo =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    if (canDefer) {
      IntentionAction action = getQuickFixFactory().createDeferFinalAssignmentFix(variable, expression);
      highlightInfo.registerFix(action, null, null, null, null);
    }
    HighlightFixUtil.registerMakeNotFinalAction(variable, highlightInfo);
    return highlightInfo;
  }

  private static boolean isFieldInitializedInAnotherMember(@NotNull PsiField field,
                                                           @NotNull PsiReferenceExpression expression,
                                                           @NotNull PsiElement codeBlock) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return false;
    boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);

    if (!isFieldStatic) {
      // constructor that delegates to another constructor cannot assign final fields
      if (enclosingConstructorOrInitializer instanceof PsiMethod method) {
        PsiMethodCallExpression chainedCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
        if (JavaPsiConstructorUtil.isChainedConstructorCall(chainedCall)) {
          return true;
        }
      }
    }

    // field can get assigned in other field initializers or in class initializers
    List<PsiMember> members = new ArrayList<>(Arrays.asList(aClass.getFields()));
    if (enclosingConstructorOrInitializer != null
        && aClass.getManager().areElementsEquivalent(enclosingConstructorOrInitializer.getContainingClass(), aClass)) {
      members.addAll(Arrays.asList(aClass.getInitializers()));
      members.sort(PsiUtil.BY_POSITION);
    }

    for (PsiMember member : members) {
      if (member == field) continue;
      PsiElement context = member instanceof PsiField f ? f.getInitializer() : ((PsiClassInitializer)member).getBody();

      if (context != null
          && member.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
          && !variableDefinitelyNotAssignedIn(field, context)) {
        return context != codeBlock;
      }
    }
    return false;
  }

  private static @NotNull Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(@NotNull Map<? super PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
                                                                                                   @NotNull PsiElement codeBlock) {
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = finalVarProblems.get(codeBlock);
    if (codeBlockProblems == null) {
      try {
        ControlFlow controlFlow = getControlFlow(codeBlock);
        codeBlockProblems = ControlFlowUtil.getInitializedTwice(controlFlow);
        codeBlockProblems = addReassignedInLoopProblems(codeBlockProblems, controlFlow);
      }
      catch (AnalysisCanceledException e) {
        codeBlockProblems = Collections.emptyList();
      }
      finalVarProblems.put(codeBlock, codeBlockProblems);
    }
    return codeBlockProblems;
  }

  private static Collection<ControlFlowUtil.VariableInfo> addReassignedInLoopProblems(
    @NotNull Collection<ControlFlowUtil.VariableInfo> codeBlockProblems,
    @NotNull ControlFlow controlFlow) {
    List<Instruction> instructions = controlFlow.getInstructions();
    for (int index = 0; index < instructions.size(); index++) {
      Instruction instruction = instructions.get(index);
      if (instruction instanceof WriteVariableInstruction wvi) {
        PsiVariable variable = wvi.variable;
        if (variable instanceof PsiLocalVariable || variable instanceof PsiField) {
          PsiElement anchor = controlFlow.getElement(index);
          if (anchor instanceof PsiAssignmentExpression assignment) {
            PsiExpression ref = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
            if (ref instanceof PsiReferenceExpression) {
              ControlFlowUtil.VariableInfo varInfo = new InitializedInLoopProblemInfo(variable, ref);
              if (!codeBlockProblems.contains(varInfo) && ControlFlowUtil.isInstructionReachable(controlFlow, index, index)) {
                if (!(codeBlockProblems instanceof HashSet)) {
                  codeBlockProblems = new HashSet<>(codeBlockProblems);
                }
                codeBlockProblems.add(varInfo);
              }
            }
          }
        }
      }
    }
    return codeBlockProblems;
  }


  static HighlightInfo.Builder checkCannotWriteToFinal(@NotNull PsiExpression expression, @NotNull PsiFile containingFile) {
    PsiExpression operand = null;
    if (expression instanceof PsiAssignmentExpression assignment) {
      operand = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      operand = ((PsiUnaryExpression)expression).getOperand();
    }
    PsiReferenceExpression reference = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiReferenceExpression.class);
    PsiVariable variable = reference == null ? null : ObjectUtils.tryCast(reference.resolve(), PsiVariable.class);
    if (variable == null || !variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    boolean canWrite = canWriteToFinal(variable, expression, reference, containingFile) && checkWriteToFinalInsideLambda(variable, reference) == null;
    if (canWrite) return null;
    String name = variable.getName();
    String description = JavaErrorBundle.message("assignment.to.final.variable", name);
    HighlightInfo.Builder highlightInfo =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference).descriptionAndTooltip(description);
    PsiElement scope = getElementVariableReferencedFrom(variable, expression);
    if (scope == null || variable instanceof PsiField) {
      HighlightFixUtil.registerMakeNotFinalAction(variable, highlightInfo);
    }
    else {
      IntentionAction action = getQuickFixFactory().createVariableAccessFromInnerClassFix(variable, scope);
      highlightInfo.registerFix(action, null, null, null, null);
    }
    return highlightInfo;
  }

  private static boolean canWriteToFinal(@NotNull PsiVariable variable,
                                         @NotNull PsiExpression expression,
                                         @NotNull PsiReferenceExpression reference,
                                         @NotNull PsiFile containingFile) {
    if (variable.hasInitializer()) {
      return variable instanceof PsiField field && !PsiAugmentProvider.canTrustFieldInitializer(field);
    }
    if (variable instanceof PsiParameter) return false;
    PsiElement scope = getElementVariableReferencedFrom(variable, expression);
    if (variable instanceof PsiField field) {
      // if inside some field initializer
      if (HighlightUtil.findEnclosingFieldInitializer(expression) != null) return true;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return true;
      // assignment from within inner class is illegal always
      if (scope != null && !containingFile.getManager().areElementsEquivalent(scope, containingClass)) return false;
      PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null &&
             !(enclosingCtrOrInitializer instanceof PsiMethod method &&
               JavaPsiRecordUtil.isCompactConstructor(method) &&
               containingClass.isRecord()) &&
             isSameField(enclosingCtrOrInitializer, field, reference, containingFile);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = scope != null;
      return !isAccessedFromOtherClass;
    }
    return true;
  }

  private static boolean isSameField(@NotNull PsiMember enclosingCtrOrInitializer,
                                     @NotNull PsiField field,
                                     @NotNull PsiReferenceExpression reference,
                                     @NotNull PsiFile containingFile) {
    if (!containingFile.getManager().areElementsEquivalent(enclosingCtrOrInitializer.getContainingClass(), field.getContainingClass())) return false;
    return LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(reference);
  }


  static HighlightInfo.Builder checkVariableMustBeFinal(@NotNull PsiVariable variable,
                                                @NotNull PsiJavaCodeReferenceElement context,
                                                @NotNull LanguageLevel languageLevel) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    PsiElement scope = getElementVariableReferencedFrom(variable, context);
    if (scope instanceof PsiClass) {
      if (variable instanceof PsiParameter parameter) {
        PsiElement parent = variable.getParent();
        if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression &&
            !VariableAccessUtils.variableIsAssigned(variable, parameter.getDeclarationScope())) {
          return null;
        }
      }
      boolean isToBeEffectivelyFinal = JavaFeature.EFFECTIVELY_FINAL.isSufficient(languageLevel);
      if (isToBeEffectivelyFinal && isEffectivelyFinal(variable, scope, context)) {
        return null;
      }
      String description = JavaErrorBundle
        .message(isToBeEffectivelyFinal ? "variable.must.be.final.or.effectively.final" : "variable.must.be.final", context.getText());

      HighlightInfo.Builder highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(description);
      IntentionAction action = getQuickFixFactory().createVariableAccessFromInnerClassFix(variable, scope);
      highlightInfo.registerFix(action, null, null, null, null);
      return highlightInfo;
    }
    HighlightInfo.Builder finalInsideLambdaInfo = checkWriteToFinalInsideLambda(variable, context);
    if (finalInsideLambdaInfo != null) {
      return finalInsideLambdaInfo;
    }
    return checkFinalUsageInsideGuardedPattern(variable, context);
  }

  @Nullable
  private static HighlightInfo.Builder checkWriteToFinalInsideLambda(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
    if (lambdaExpression != null && !PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
      PsiElement parent = variable.getParent();
      if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) {
        return null;
      }
      PsiSwitchLabelStatementBase label =
        PsiTreeUtil.getParentOfType(context, PsiSwitchLabelStatementBase.class, true, PsiLambdaExpression.class);
      if (label != null && PsiTreeUtil.isAncestor(label.getGuardExpression(), context, false)) {
        return null;
      }
      if (!isEffectivelyFinal(variable, lambdaExpression, context)) {
        String text = JavaErrorBundle.message("lambda.variable.must.be.final");
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(text);
        IntentionAction action1 = getQuickFixFactory().createVariableAccessFromInnerClassFix(variable, lambdaExpression);
        builder.registerFix(action1, null, null, null, null);
        IntentionAction action = getQuickFixFactory().createMakeVariableEffectivelyFinalFix(variable);
        if (action != null) {
          builder.registerFix(action, null, null, null, null);
        }
        ErrorFixExtensionPoint.registerFixes(builder, context, "lambda.variable.must.be.final");
        return builder;
      }
    }
    return null;
  }

  /**
   * 14.30.1 Kinds of Patterns
   * <p>Any variable that is used but not declared in the guarding expression of a guarded pattern must either be final or effectively final.
   */
  @Nullable
  private static HighlightInfo.Builder checkFinalUsageInsideGuardedPattern(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    PsiSwitchLabelStatementBase refLabel = PsiTreeUtil.getParentOfType(context, PsiSwitchLabelStatementBase.class);

    if (refLabel == null) return null;
    PsiExpression guardExpression = refLabel.getGuardExpression();
    if (!PsiTreeUtil.isAncestor(guardExpression, context, false)) return null;
    //this assignment is covered by com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.checkOutsideDeclaredCantBeAssignmentInGuard
    boolean isAssignment = context instanceof PsiReferenceExpression ref && PsiUtil.isAccessedForWriting(ref);
    if (!isAssignment && !PsiTreeUtil.isAncestor(guardExpression, variable, false) &&
        !isEffectivelyFinal(variable, refLabel, context)) {
      String message = JavaErrorBundle.message("guarded.pattern.variable.must.be.final");
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(message);
      IntentionAction action = getQuickFixFactory().createVariableAccessFromInnerClassFix(variable, refLabel);
      builder.registerFix(action, null, null, null, null);
      IntentionAction action2 = getQuickFixFactory().createMakeVariableEffectivelyFinalFix(variable);
      if (action2 != null) {
        builder.registerFix(action2, null, null, null, null);
      }
      ErrorFixExtensionPoint.registerFixes(builder, context, "guarded.pattern.variable.must.be.final");
      return builder;
    }
    return null;
  }

  public static boolean isEffectivelyFinal(@NotNull PsiVariable variable, @NotNull PsiElement scope, @Nullable PsiJavaCodeReferenceElement context) {
    boolean effectivelyFinal;
    if (variable instanceof PsiParameter parameter) {
      effectivelyFinal = !VariableAccessUtils.variableIsAssigned(variable, parameter.getDeclarationScope());
    }
    else {
      PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, context);
      ControlFlow controlFlow;
      try {
        if (codeBlock == null) return true;
        controlFlow = getControlFlow(codeBlock);
      }
      catch (AnalysisCanceledException e) {
        return true;
      }

      Collection<ControlFlowUtil.VariableInfo> initializedTwice = ControlFlowUtil.getInitializedTwice(controlFlow);
      effectivelyFinal = !initializedTwice.contains(new ControlFlowUtil.VariableInfo(variable, null));
      if (effectivelyFinal) {
        for (PsiReferenceExpression expression : ControlFlowUtil.getReadBeforeWriteLocals(controlFlow)) {
          if (expression.resolve() == variable) {
            return PsiUtil.isAccessedForReading(expression);
          }
        }
        effectivelyFinal = !VariableAccessUtils.variableIsAssigned(variable, scope);
        if (effectivelyFinal) {
          Ref<Boolean> stopped = new Ref<>(false);
          codeBlock.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
              if (expression.isReferenceTo(variable) &&
                  PsiUtil.isAccessedForWriting(expression) &&
                  ControlFlowUtil.isVariableAssignedInLoop(expression, variable)) {
                stopWalking();
                stopped.set(true);
              }
            }
          });
          return !stopped.get();
        }
      }
    }
    return effectivelyFinal;
  }

  /**
   * @param variable variable
   * @param context the context that reference to the variable
   * @return inner class, lambda expression, or switch label that refers to the variable
   */
  public static @Nullable PsiElement getElementVariableReferencedFrom(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    PsiElement[] scope;
    if (variable instanceof PsiResourceVariable resourceVariable) {
      scope = resourceVariable.getDeclarationScope();
    }
    else if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      scope = new PsiElement[]{parent != null ? parent.getParent() : null}; // code block or for statement
    }
    else if (variable instanceof PsiParameter parameter) {
      scope = new PsiElement[]{parameter.getDeclarationScope()};
    }
    else {
      scope = new PsiElement[]{variable.getParent()};
    }
    if (scope.length < 1 || scope[0] == null || scope[0].getContainingFile() != context.getContainingFile()) return null;
    PsiElement parent = context.getParent();
    PsiElement prevParent = context;
    outer:
    while (parent != null) {
      for (PsiElement scopeElement : scope) {
        if (parent.equals(scopeElement)) break outer;
      }
      if (parent instanceof PsiClass && !(prevParent instanceof PsiExpressionList && parent instanceof PsiAnonymousClass)) {
        return parent;
      }
      if (parent instanceof PsiLambdaExpression) {
        return parent;
      }
      if (parent instanceof PsiSwitchLabelStatementBase label && label.getGuardExpression() == prevParent) {
        return parent;
      }
      prevParent = parent;
      parent = parent.getParent();
    }
    return null;
  }

  static HighlightInfo.Builder checkInitializerCompleteNormally(@NotNull PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    // unhandled exceptions already reported
    try {
      ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
      int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
      if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
        String description = JavaErrorBundle.message("initializer.must.be.able.to.complete.normally");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(body).descriptionAndTooltip(description);
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
    return null;
  }

  /**
   * A kind of final variable problem returned from {@link #getFinalVariableProblemsInBlock(Map, PsiElement)}
   * which designates a final variable which is initialized in a loop.
   */
  private static class InitializedInLoopProblemInfo extends ControlFlowUtil.VariableInfo {
    InitializedInLoopProblemInfo(@NotNull PsiVariable variable, @Nullable PsiElement expression) {
      super(variable, expression);
    }
  }
}
