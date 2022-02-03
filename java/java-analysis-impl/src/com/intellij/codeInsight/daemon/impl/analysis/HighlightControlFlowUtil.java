// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.actions.ChangeModifierRequest;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.lang.jvm.actions.MemberRequestsKt;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
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
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private HighlightControlFlowUtil() { }

  static HighlightInfo checkMissingReturnStatement(@Nullable PsiCodeBlock body, @Nullable PsiType returnType) {
    if (body == null || returnType == null || PsiType.VOID.equals(returnType.getDeepComponentType())) {
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
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(message).create();
        PsiElement parent = body.getParent();
        if (parent instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent;
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddReturnFix(method));
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, true));
        }
        if (parent instanceof PsiLambdaExpression) {
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddReturnFix((PsiLambdaExpression)parent));
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

  static HighlightInfo checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
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
              HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(condition)
                .descriptionAndTooltip(JavaErrorBundle.message("unreachable.statement.false.condition")).create();
              QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createSimplifyBooleanFix(condition, false));
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
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(
          info, QUICK_FIX_FACTORY.createDeleteFix(unreachableStatement, QuickFixBundle.message("delete.unreachable.statement.fix.text")));
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
      if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic, __->true)) return true;
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
        List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
        for (PsiMethod redirectedConstructor : redirectedConstructors) {
          PsiCodeBlock body = redirectedConstructor.getBody();
          if (body != null && variableDefinitelyAssignedIn(field, body)) continue nextConstructor;
        }
        if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody)) {
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
                                                           && variableDefinitelyAssignedIn(field, initializer.getBody())) != null;
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(@NotNull PsiClass aClass,
                                                                   @NotNull PsiField field,
                                                                   boolean fieldStatic,
                                                                   @NotNull Predicate<? super PsiField> condition) {
    PsiField[] fields = aClass.getFields();
    for (PsiField psiField : fields) {
      if (psiField != field
          && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic
          && variableDefinitelyAssignedIn(field, psiField)
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
    return info.visitedConstructors.indexOf(info.recursivelyCalledConstructor) <=
           info.visitedConstructors.indexOf(constructor);
  }

  public static boolean isAssigned(@NotNull PsiParameter parameter) {
    ParamWriteProcessor processor = new ParamWriteProcessor();
    ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), true).forEach(processor);
    return processor.isWriteRefFound();
  }

  private static class ParamWriteProcessor implements Processor<PsiReference> {
    private volatile boolean myIsWriteRefFound;
    @Override
    public boolean process(PsiReference reference) {
      PsiElement element = reference.getElement();
      if (element instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
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
    try {
      ControlFlow controlFlow = getControlFlow(context);
      return ControlFlowUtil.isVariableDefinitelyAssigned(variable, controlFlow);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  private static boolean variableDefinitelyNotAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    try {
      ControlFlow controlFlow = getControlFlow(context);
      return ControlFlowUtil.isVariableDefinitelyNotAssigned(variable, controlFlow);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  static HighlightInfo checkRecordComponentInitialized(PsiRecordComponent component) {
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
    if (field == null) return null;
    if (variableDefinitelyAssignedIn(field, body)) return null;
    String description = JavaErrorBundle.message("record.component.not.initialized", field.getName());
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(description).create();
  }

  static HighlightInfo checkFinalFieldInitialized(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    if (isFieldInitializedAfterObjectConstruction(field)) return null;

    String description = JavaErrorBundle.message("variable.not.initialized", field.getName());
    TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
    HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createCreateConstructorParameterFromFieldFix(field));
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createInitializeFinalFieldInConstructorFix(field));
    QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddVariableInitializerFix(field));
    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !containingClass.isInterface()) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.FINAL, false, false));
    }
    return highlightInfo;
  }


  public static HighlightInfo checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                  @NotNull PsiVariable variable,
                                                                  @NotNull Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                                  @NotNull PsiFile containingFile) {
    return checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems, containingFile, false);
  }

  public static HighlightInfo checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                  @NotNull PsiVariable variable,
                                                                  @NotNull Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
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
      PsiElement scope = variable instanceof PsiField
                               ? ((PsiField)variable).getContainingClass()
                               : variable.getParent() != null ? variable.getParent().getParent() : null;
      while (scope instanceof PsiCodeBlock && scope.getParent() instanceof PsiSwitchBlock) {
        scope = PsiTreeUtil.getParentOfType(scope, PsiCodeBlock.class);
      }

      topBlock = FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile ? scope : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
      if (variable instanceof PsiField) {
        // non-final field already initialized with default value
        if (!ignoreFinality && !variable.hasModifierProperty(PsiModifier.FINAL)) return null;
        // final field may be initialized in ctor or class initializer only
        // if we're inside non-ctr method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && HighlightUtil.findEnclosingFieldInitializer(expression) == null) {
          return null;
        }
        if (topBlock == null) return null;
        PsiElement parent = topBlock.getParent();
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, ((PsiField)variable).getContainingClass())) return null;
        PsiCodeBlock block;
        PsiClass aClass;
        if (parent instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)parent;
          if (!containingFile.getManager().areElementsEquivalent(constructor.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          // static variables already initialized in class initializers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return null;
          // as a last chance, field may be initialized in this() call
          List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
          for (PsiMethod redirectedConstructor : redirectedConstructors) {
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            if (JavaPsiRecordUtil.isCompactConstructor(redirectedConstructor)) return null;
            PsiCodeBlock body = redirectedConstructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return null;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer) {
          PsiClassInitializer classInitializer = (PsiClassInitializer)parent;
          if (!containingFile.getManager().areElementsEquivalent(classInitializer.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();

          if (aClass == null || isFieldInitializedInOtherFieldInitializer(aClass, (PsiField)variable, variable.hasModifierProperty(PsiModifier.STATIC), field -> startOffset > field.getTextOffset())) return null;
        }
        else {
          // field reference outside code block
          // check variable initialized before its usage
          PsiField field = (PsiField)variable;

          aClass = field.getContainingClass();
          PsiField anotherField = PsiTreeUtil.getTopmostParentOfType(expression, PsiField.class);
          if (aClass == null ||
              isFieldInitializedInOtherFieldInitializer(aClass, field, field.hasModifierProperty(PsiModifier.STATIC), psiField -> startOffset > psiField.getTextOffset())) {
            return null;
          }
          if (anotherField != null && !anotherField.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.STATIC) &&
              isFieldInitializedInClassInitializer(field, true, aClass.getInitializers())) {
            return null;
          }

          int offset = startOffset;
          if (anotherField != null && anotherField.getContainingClass() == aClass && !field.hasModifierProperty(PsiModifier.STATIC)) {
            offset = 0;
          }
          block = null;
          // initializers will be checked later
          PsiMethod[] constructors = aClass.getConstructors();
          for (PsiMethod constructor : constructors) {
            // variable must be initialized before its usage
            if (offset < constructor.getTextRange().getStartOffset()) continue;
            PsiCodeBlock body = constructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return null;
            }
            // as a last chance, field may be initialized in this() call
            List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
            for (PsiMethod redirectedConstructor : redirectedConstructors) {
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
          PsiClassInitializer[] initializers = aClass.getInitializers();
          for (PsiClassInitializer initializer : initializers) {
            PsiCodeBlock body = initializer.getBody();
            if (body == block) break;
            // variable referenced in initializer must be initialized in initializer preceding assignment
            // variable referenced in field initializer or in class initializer
            boolean shouldCheckInitializerOrder = block == null || block.getParent() instanceof PsiClassInitializer;
            if (shouldCheckInitializerOrder && startOffset < initializer.getTextRange().getStartOffset()) continue;
            if (initializer.hasModifierProperty(PsiModifier.STATIC)
                == variable.hasModifierProperty(PsiModifier.STATIC)) {
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
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddVariableInitializerFix(variable));
      if (variable instanceof PsiLocalVariable) {
        QuickFixAction.registerQuickFixAction(highlightInfo, HighlightFixUtil.createInsertSwitchDefaultFix(variable, topBlock, expression));
      }
      if (variable instanceof PsiField) {
        ChangeModifierRequest request = MemberRequestsKt.modifierRequest(JvmModifier.FINAL, false);
        QuickFixAction.registerQuickFixActions(highlightInfo, null, JvmElementActionFactories.createModifierActions((PsiField)variable, request));
      }
      return highlightInfo;
    }

    return null;
  }

  private static boolean inInnerClass(@NotNull PsiElement psiElement, @Nullable PsiClass containingClass) {
    for (PsiElement element = psiElement;element != null;element = element.getParent()) {
      if (element instanceof PsiClass) {
        boolean innerClass = !psiElement.getManager().areElementsEquivalent(element, containingClass);
        if (innerClass) {
          if (element instanceof PsiAnonymousClass) {
            if (PsiTreeUtil.isAncestor(((PsiAnonymousClass)element).getArgumentList(), psiElement, false)) {
              continue;
            }
            return !insideClassInitialization(containingClass, (PsiClass)element);
          }
          PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLambdaExpression.class);
          return lambdaExpression == null || !insideClassInitialization(containingClass, (PsiClass)element);
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
               member instanceof PsiMethod && ((PsiMethod)member).isConstructor() ||
               member instanceof PsiClassInitializer;
      }
      member = PsiTreeUtil.getParentOfType(member, PsiMember.class, true);
    }
    return false;
  }

  public static boolean isReassigned(@NotNull PsiVariable variable,
                                     @NotNull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      if (parent == null) return false;
      PsiElement declarationScope = parent.getParent();
      if (declarationScope == null) return false;
      Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
    }
    if (variable instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)variable;
      return isAssigned(parameter);
    }
    return false;
  }


  public static HighlightInfo checkFinalVariableMightAlreadyHaveBeenAssignedTo(@NotNull PsiVariable variable,
                                                                               @NotNull PsiReferenceExpression expression,
                                                                               @NotNull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return null;

    PsiElement scope = variable instanceof PsiField ? variable.getParent() :
                             variable.getParent() == null ? null : variable.getParent().getParent();
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
    else if (!(variable instanceof PsiField && isFieldInitializedInAnotherMember((PsiField)variable, expression, codeBlock))) {
      return null;
    }

    String description =
      JavaErrorBundle.message(inLoop ? "variable.assigned.in.loop" : "variable.already.assigned", variable.getName());
    HighlightInfo highlightInfo =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    if (canDefer) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeferFinalAssignmentFix(variable, expression));
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
      if (enclosingConstructorOrInitializer instanceof PsiMethod) {
        PsiMethodCallExpression chainedCall =
          JavaPsiConstructorUtil.findThisOrSuperCallInConstructor((PsiMethod)enclosingConstructorOrInitializer);
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
      PsiElement context = member instanceof PsiField ? ((PsiField)member).getInitializer()
                                                      : ((PsiClassInitializer)member).getBody();

      if (context != null
          && member.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
          && !variableDefinitelyNotAssignedIn(field, context)) {
        return context != codeBlock;
      }
    }
    return false;
  }

  private static @NotNull Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(@NotNull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
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
      if (instruction instanceof WriteVariableInstruction) {
        PsiVariable variable = ((WriteVariableInstruction)instruction).variable;
        if (variable instanceof PsiLocalVariable || variable instanceof PsiField) {
          PsiElement anchor = controlFlow.getElement(index);
          if (anchor instanceof PsiAssignmentExpression) {
            PsiExpression ref = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)anchor).getLExpression());
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


  static HighlightInfo checkCannotWriteToFinal(@NotNull PsiExpression expression, @NotNull PsiFile containingFile) {
    PsiExpression operand = null;
    if (expression instanceof PsiAssignmentExpression) {
      operand = ((PsiAssignmentExpression)expression).getLExpression();
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
    HighlightInfo highlightInfo =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference).descriptionAndTooltip(description).create();
    PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, expression);
    if (innerClass == null || variable instanceof PsiField) {
      HighlightFixUtil.registerMakeNotFinalAction(variable, highlightInfo);
    }
    else {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, innerClass));
    }
    return highlightInfo;
  }

  private static boolean canWriteToFinal(@NotNull PsiVariable variable,
                                         @NotNull PsiExpression expression,
                                         @NotNull  PsiReferenceExpression reference,
                                         @NotNull PsiFile containingFile) {
    if (variable.hasInitializer()) return false;
    if (variable instanceof PsiParameter) return false;
    PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, expression);
    if (variable instanceof PsiField) {
      // if inside some field initializer
      if (HighlightUtil.findEnclosingFieldInitializer(expression) != null) return true;
      // assignment from within inner class is illegal always
      PsiField field = (PsiField)variable;
      if (innerClass != null && !containingFile.getManager().areElementsEquivalent(innerClass, field.getContainingClass())) return false;
      PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null &&
             !(enclosingCtrOrInitializer instanceof PsiMethod &&
               JavaPsiRecordUtil.isCompactConstructor((PsiMethod)enclosingCtrOrInitializer)) &&
             isSameField(enclosingCtrOrInitializer, field, reference, containingFile);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = innerClass != null;
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


  static HighlightInfo checkVariableMustBeFinal(@NotNull PsiVariable variable,
                                                @NotNull PsiJavaCodeReferenceElement context,
                                                @NotNull LanguageLevel languageLevel) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, context);
    if (innerClass instanceof PsiClass) {
      if (variable instanceof PsiParameter) {
        PsiElement parent = variable.getParent();
        if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression &&
            !VariableAccessUtils.variableIsAssigned(variable, ((PsiParameter)variable).getDeclarationScope())) {
          return null;
        }
      }
      boolean isToBeEffectivelyFinal = languageLevel.isAtLeast(LanguageLevel.JDK_1_8);
      if (isToBeEffectivelyFinal && isEffectivelyFinal(variable, innerClass, context)) {
        return null;
      }
      String description = JavaErrorBundle
        .message(isToBeEffectivelyFinal ? "variable.must.be.final.or.effectively.final" : "variable.must.be.final", context.getText());

      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, innerClass));
      return highlightInfo;
    }
    HighlightInfo finalInsideLambdaInfo = checkWriteToFinalInsideLambda(variable, context);
    if (finalInsideLambdaInfo != null) {
      return finalInsideLambdaInfo;
    }
    return checkFinalUsageInsideGuardedPattern(variable, context);
  }

  @Nullable
  private static HighlightInfo checkWriteToFinalInsideLambda(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
    if (lambdaExpression != null && !PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
      PsiElement parent = variable.getParent();
      if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) {
        return null;
      }
      if (PsiTreeUtil.getParentOfType(context, PsiGuardedPattern.class, true, PsiLambdaExpression.class) != null) {
        return null;
      }
      if (!isEffectivelyFinal(variable, lambdaExpression, context)) {
        String text = JavaErrorBundle.message("lambda.variable.must.be.final");
        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(text).create();
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, lambdaExpression));
        return ErrorFixExtensionPoint.registerFixes(highlightInfo, context, "lambda.variable.must.be.final");
      }
    }
    return null;
  }

  /**
   * 14.30.1 Kinds of Patterns
   * <p>Any variable that is used but not declared in the guarding expression of a guarded pattern must either be final or effectively final.
   */
  @Nullable
  private static HighlightInfo checkFinalUsageInsideGuardedPattern(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    PsiGuardedPattern guardedPattern = PsiTreeUtil.getParentOfType(context, PsiGuardedPattern.class);
    if (guardedPattern == null) return null;
    PsiPatternVariable patternVariable = JavaPsiPatternUtil.getPatternVariable(guardedPattern);
    if (variable != patternVariable && !isEffectivelyFinal(variable, guardedPattern, context)) {
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context)
        .descriptionAndTooltip(JavaErrorBundle.message("guarded.pattern.variable.must.be.final")).create();
      // todo quick-fix may be registered here, but
      // todo com.intellij.codeInsight.intention.QuickFixFactory.createVariableAccessFromInnerClassFix should be fix beforehand
      return highlightInfo;
    }
    return null;
  }

  public static boolean isEffectivelyFinal(@NotNull PsiVariable variable, @NotNull PsiElement scope, @Nullable PsiJavaCodeReferenceElement context) {
    boolean effectivelyFinal;
    if (variable instanceof PsiParameter) {
      effectivelyFinal = !VariableAccessUtils.variableIsAssigned(variable, ((PsiParameter)variable).getDeclarationScope());
    }
    else {
      ControlFlow controlFlow;
      try {
        PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, context);
        if (codeBlock == null) return true;
        controlFlow = getControlFlow(codeBlock);
      }
      catch (AnalysisCanceledException e) {
        return true;
      }

      Collection<ControlFlowUtil.VariableInfo> initializedTwice = ControlFlowUtil.getInitializedTwice(controlFlow);
      effectivelyFinal = !initializedTwice.contains(new ControlFlowUtil.VariableInfo(variable, null));
      if (effectivelyFinal) {
        List<PsiReferenceExpression> readBeforeWriteLocals = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
        for (PsiReferenceExpression expression : readBeforeWriteLocals) {
          if (expression.resolve() == variable) {
            return PsiUtil.isAccessedForReading(expression);
          }
        }
        effectivelyFinal = !VariableAccessUtils.variableIsAssigned(variable, scope);
        if (effectivelyFinal) {
          return ReferencesSearch.search(variable).allMatch(ref -> {
            PsiElement element = ref.getElement();
            if (element instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
              return !ControlFlowUtil.isVariableAssignedInLoop((PsiReferenceExpression)element, variable);
            }
            return true;
          });
        }
      }
    }
    return effectivelyFinal;
  }

  public static PsiElement getInnerClassVariableReferencedFrom(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    PsiElement[] scope;
    if (variable instanceof PsiResourceVariable) {
      scope = ((PsiResourceVariable)variable).getDeclarationScope();
    }
    else if (variable instanceof PsiLocalVariable) {
      PsiElement parent = variable.getParent();
      scope = new PsiElement[]{parent != null ? parent.getParent() : null}; // code block or for statement
    }
    else if (variable instanceof PsiParameter) {
      scope = new PsiElement[]{((PsiParameter)variable).getDeclarationScope()};
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
      prevParent = parent;
      parent = parent.getParent();
    }
    return null;
  }

  static HighlightInfo checkInitializerCompleteNormally(@NotNull PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    // unhandled exceptions already reported
    try {
      ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
      int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
      if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
        String description = JavaErrorBundle.message("initializer.must.be.able.to.complete.normally");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(body).descriptionAndTooltip(description).create();
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
