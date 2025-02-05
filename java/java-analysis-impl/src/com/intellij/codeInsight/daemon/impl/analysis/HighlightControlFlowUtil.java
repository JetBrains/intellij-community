// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class HighlightControlFlowUtil {

  private static QuickFixFactory getQuickFixFactory() {
    return QuickFixFactory.getInstance();
  }

  private HighlightControlFlowUtil() { }

  /**
   * @deprecated use {@link ControlFlowFactory#getControlFlowNoConstantEvaluate(PsiElement)}
   */
  @Deprecated
  public static @NotNull ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    return ControlFlowFactory.getControlFlowNoConstantEvaluate(body);
  }

  private static @NotNull ControlFlow getControlFlow(@NotNull PsiElement context) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getControlFlow(context, policy, ControlFlowOptions.create(true, true, true));
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
        for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
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

  public static boolean isAssigned(@NotNull PsiParameter parameter) {
    ParamWriteProcessor processor = new ParamWriteProcessor();
    ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), true).forEach(processor);
    return processor.isWriteRefFound();
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  private static PsiField findEnclosingFieldInitializer(@NotNull PsiElement entry) {
    PsiElement element = entry;
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField field) {
        if (element == field.getInitializer()) return field;
        if (field instanceof PsiEnumConstant enumConstant && element == enumConstant.getArgumentList()) return field;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  public static @NotNull TextRange getFixRange(@NotNull PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    TextRange range = element.getTextRange();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return range.grown(1);
    }
    return range;
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

  static HighlightInfo.Builder checkFinalFieldInitialized(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    if (isFieldInitializedAfterObjectConstruction(field)) return null;
    if (PsiUtilCore.hasErrorElementChild(field)) return null;
    String description = JavaErrorBundle.message("variable.not.initialized", field.getName());
    TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description);
    IntentionAction action3 = getQuickFixFactory().createCreateConstructorParameterFromFieldFix(field);
    builder.registerFix(action3, null, null, getFixRange(field), null);
    IntentionAction action2 = getQuickFixFactory().createInitializeFinalFieldInConstructorFix(field);
    builder.registerFix(action2, null, null, getFixRange(field), null);
    IntentionAction action1 = getQuickFixFactory().createAddVariableInitializerFix(field);
    builder.registerFix(action1, null, null, null, null);
    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !containingClass.isInterface()) {
      IntentionAction action = getQuickFixFactory().createModifierListFix(field, PsiModifier.FINAL, false, false);
      builder.registerFix(action, null, null, null, null);
    }
    return builder;
  }


  static HighlightInfo.Builder checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                   @NotNull PsiVariable variable,
                                                                   @NotNull Map<? super PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems) {
    if (isInitializedBeforeUsage(expression, variable, uninitializedVarProblems, false)) return null;
    return createNotInitializedError(expression, variable);
  }

  public static boolean isInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                 @NotNull PsiVariable variable,
                                                 @NotNull Map<? super PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                 boolean ignoreFinality) {
    if (variable instanceof ImplicitVariable) return true;
    if (!PsiUtil.isAccessedForReading(expression)) return true;
    int startOffset = expression.getTextRange().getStartOffset();
    PsiElement topBlock = getTopBlock(expression, variable);
    if (topBlock == null) return true;
    if (!variable.hasInitializer()) {
      if (variable instanceof PsiField field) {
        // non-final field already initialized with default value
        if (!ignoreFinality && !variable.hasModifierProperty(PsiModifier.FINAL)) return true;
        // a final field may be initialized in ctor or class initializer only
        // if we're inside non-ctr method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && findEnclosingFieldInitializer(expression) == null) {
          return true;
        }
        PsiElement parent = topBlock.getParent();
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, field.getContainingClass())) return true;
        PsiCodeBlock block;
        PsiClass aClass;
        if (parent instanceof PsiMethod constructor) {
          if (!constructor.getManager().areElementsEquivalent(constructor.getContainingClass(), field.getContainingClass())) return true;
          // static variables already initialized in class initializers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return true;
          // as a last chance, field may be initialized in this() call
          for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            if (JavaPsiRecordUtil.isCompactConstructor(redirectedConstructor)) return true;
            PsiCodeBlock body = redirectedConstructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body, true)) {
              return true;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer classInitializer) {
          if (!classInitializer.getManager().areElementsEquivalent(classInitializer.getContainingClass(), field.getContainingClass())) {
            return true;
          }
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();

          if (aClass == null || isFieldInitializedInOtherFieldInitializer(aClass, field, variable.hasModifierProperty(PsiModifier.STATIC),
                                                                          f -> startOffset > f.getTextOffset())) {
            return true;
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
            return true;
          }
          if (anotherField != null
              && !anotherField.hasModifierProperty(PsiModifier.STATIC)
              && field.hasModifierProperty(PsiModifier.STATIC)
              && isFieldInitializedInClassInitializer(field, true, aClass.getInitializers())) {
            return true;
          }
          if (anotherField != null && anotherField.hasInitializer() && !PsiAugmentProvider.canTrustFieldInitializer(anotherField)) {
            return true;
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
              return true;
            }
            // as a last chance, field may be initialized in this() call
            for (PsiMethod redirectedConstructor : JavaPsiConstructorUtil.getChainedConstructors(constructor)) {
              // variable must be initialized before its usage
              if (offset < redirectedConstructor.getTextRange().getStartOffset()) continue;
              PsiCodeBlock redirectedBody = redirectedConstructor.getBody();
              if (redirectedBody != null && variableDefinitelyAssignedIn(variable, redirectedBody)) {
                return true;
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
              if (variableDefinitelyAssignedIn(variable, body)) return true;
            }
          }
        }
      }
    }
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
    return !codeBlockProblems.contains(expression);
  }

  private static @Nullable PsiElement getTopBlock(@NotNull PsiReferenceExpression expression, @NotNull PsiVariable variable) {
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

      topBlock = FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile
                 ? scope
                 : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    }
    return topBlock;
  }

  private static HighlightInfo.@NotNull Builder createNotInitializedError(@NotNull PsiReferenceExpression expression, 
                                                                          @NotNull PsiVariable variable) {
    String name = expression.getElement().getText();
    String description = JavaErrorBundle.message("variable.not.initialized", name);
    HighlightInfo.Builder builder =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    if (!(variable instanceof LightRecordField)) {
      IntentionAction action1 = getQuickFixFactory().createAddVariableInitializerFix(variable);
      builder.registerFix(action1, null, null, null, null);
    }
    if (variable instanceof PsiLocalVariable) {
      PsiElement topBlock = getTopBlock(expression, variable);
      if (topBlock != null) {
        IntentionAction action = HighlightFixUtil.createInsertSwitchDefaultFix(variable, topBlock, expression);
        if (action != null) {
          builder.registerFix(action, null, null, null, null);
        }
      }
    }
    if (variable instanceof PsiField field) {
      ChangeModifierRequest request = MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false);
      QuickFixAction.registerQuickFixActions(builder, null, JvmElementActionFactories.createModifierActions(field, request));
    }
    return builder;
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
