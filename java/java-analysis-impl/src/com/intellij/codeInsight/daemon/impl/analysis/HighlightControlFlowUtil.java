/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author cdr
 * @since Aug 8, 2002
 */
public class HighlightControlFlowUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private HighlightControlFlowUtil() { }

  @Nullable
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
        String message = JavaErrorMessages.message("missing.return.statement");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(message).create();
        PsiElement parent = body.getParent();
        if (parent instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)parent;
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddReturnFix(method));
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMethodReturnFix(method, PsiType.VOID, true));
        }
        return info;
      }
    }
    catch (AnalysisCanceledException ignored) { }

    return null;
  }

  @NotNull
  public static ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, policy, false, false);
  }

  @NotNull
  private static ControlFlow getControlFlow(@NotNull PsiElement context) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
  }

  static HighlightInfo checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
    if (codeBlock == null) return null;
    // do not compute constant expressions for if() statement condition
    // see JLS 14.20 Unreachable Statements
    try {
      AllVariablesControlFlowPolicy policy = AllVariablesControlFlowPolicy.getInstance();
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(codeBlock.getProject()).getControlFlow(codeBlock, policy, false, false);
      final PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
      if (unreachableStatement != null) {
        String description = JavaErrorMessages.message("unreachable.statement");
        PsiElement keyword = null;
        if (unreachableStatement instanceof PsiIfStatement ||
            unreachableStatement instanceof PsiSwitchStatement ||
            unreachableStatement instanceof PsiLoopStatement) {
          keyword = unreachableStatement.getFirstChild();
        }
        final PsiElement element = keyword != null ? keyword : unreachableStatement;
        final HighlightInfo info =
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
    final boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      // field might be assigned in the other field initializers
      if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic)) return true;
    }
    final PsiClassInitializer[] initializers;
    if (aClass != null) {
      initializers = aClass.getInitializers();
    }
    else {
      return false;
    }
    if (isFieldInitializedInClassInitializer(field, isFieldStatic, Arrays.stream(initializers))) return true;
    if (isFieldStatic) {
      return false;
    }
    else {
      // instance field should be initialized at the end of the each constructor
      final PsiMethod[] constructors = aClass.getConstructors();

      if (constructors.length == 0) return false;
      nextConstructor:
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock ctrBody = constructor.getBody();
        if (ctrBody == null) return false;
        final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
        for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
          PsiMethod redirectedConstructor = redirectedConstructors.get(j);
          final PsiCodeBlock body = redirectedConstructor.getBody();
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
                                                              Stream<PsiClassInitializer> initializers) {
    return initializers.anyMatch(initializer -> initializer.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
                                                && variableDefinitelyAssignedIn(field, initializer.getBody()));
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(@NotNull PsiClass aClass,
                                                                   @NotNull PsiField field,
                                                                   final boolean fieldStatic) {
    PsiField[] fields = aClass.getFields();
    for (PsiField psiField : fields) {
      if (psiField != field
          && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic
          && variableDefinitelyAssignedIn(field, psiField)) {
        return true;
      }
    }
    return false;
  }

  static boolean isRecursivelyCalledConstructor(@NotNull PsiMethod constructor) {
    final JavaHighlightUtil.ConstructorVisitorInfo info = new JavaHighlightUtil.ConstructorVisitorInfo();
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
      final PsiElement element = reference.getElement();
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


  @Nullable
  static HighlightInfo checkFinalFieldInitialized(@NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    if (isFieldInitializedAfterObjectConstruction(field)) return null;

    String description = JavaErrorMessages.message("variable.not.initialized", field.getName());
    TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
    HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createCreateConstructorParameterFromFieldFix(field));
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createInitializeFinalFieldInConstructorFix(field));
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !containingClass.isInterface()) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.FINAL, false, false));
    }
    QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddVariableInitializerFix(field));
    return highlightInfo;
  }


  @Nullable
  public static HighlightInfo checkVariableInitializedBeforeUsage(@NotNull PsiReferenceExpression expression,
                                                                  @NotNull PsiVariable variable,
                                                                  @NotNull Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
                                                                  @NotNull PsiFile containingFile) {
    if (variable instanceof ImplicitVariable) return null;
    if (!PsiUtil.isAccessedForReading(expression)) return null;
    int startOffset = expression.getTextRange().getStartOffset();
    final PsiElement topBlock;
    if (variable.hasInitializer()) {
      topBlock = PsiUtil.getVariableCodeBlock(variable, variable);
      if (topBlock == null) return null;
    }
    else {
      PsiElement scope = variable instanceof PsiField
                               ? ((PsiField)variable).getContainingClass()
                               : variable.getParent() != null ? variable.getParent().getParent() : null;
      if (scope instanceof PsiCodeBlock && scope.getParent() instanceof PsiSwitchStatement) {
        scope = PsiTreeUtil.getParentOfType(scope, PsiCodeBlock.class);
      }

      topBlock = FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile ? scope : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
      if (variable instanceof PsiField) {
        // non final field already initialized with default value
        if (!variable.hasModifierProperty(PsiModifier.FINAL)) return null;
        // final field may be initialized in ctor or class initializer only
        // if we're inside non-ctr method, skip it
        if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
            && HighlightUtil.findEnclosingFieldInitializer(expression) == null) {
          return null;
        }
        if (topBlock == null) return null;
        final PsiElement parent = topBlock.getParent();
        // access to final fields from inner classes always allowed
        if (inInnerClass(expression, ((PsiField)variable).getContainingClass())) return null;
        final PsiCodeBlock block;
        final PsiClass aClass;
        if (parent instanceof PsiMethod) {
          PsiMethod constructor = (PsiMethod)parent;
          if (!containingFile.getManager().areElementsEquivalent(constructor.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          // static variables already initialized in class initializers
          if (variable.hasModifierProperty(PsiModifier.STATIC)) return null;
          // as a last chance, field may be initialized in this() call
          final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
          for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
            PsiMethod redirectedConstructor = redirectedConstructors.get(j);
            // variable must be initialized before its usage
            //???
            //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
            PsiCodeBlock body = redirectedConstructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return null;
            }
          }
          block = constructor.getBody();
          aClass = constructor.getContainingClass();
        }
        else if (parent instanceof PsiClassInitializer) {
          final PsiClassInitializer classInitializer = (PsiClassInitializer)parent;
          if (!containingFile.getManager().areElementsEquivalent(classInitializer.getContainingClass(), ((PsiField)variable).getContainingClass())) return null;
          block = classInitializer.getBody();
          aClass = classInitializer.getContainingClass();
        }
        else {
          // field reference outside code block
          // check variable initialized before its usage
          final PsiField field = (PsiField)variable;

          aClass = field.getContainingClass();
          final PsiField anotherField = PsiTreeUtil.getTopmostParentOfType(expression, PsiField.class);
          if (aClass == null ||
              isFieldInitializedInOtherFieldInitializer(aClass, field, field.hasModifierProperty(PsiModifier.STATIC))) {
            return null;
          }
          if (anotherField != null && !anotherField.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.STATIC) &&
              isFieldInitializedInClassInitializer(field, true, Arrays.stream(aClass.getInitializers()))) {
            return null;
          }

          int offset = startOffset;
          if (anotherField != null && anotherField.getContainingClass() == aClass && !field.hasModifierProperty(PsiModifier.STATIC)) {
            offset = 0;
          }
          block = null;
          // initializers will be checked later
          final PsiMethod[] constructors = aClass.getConstructors();
          for (PsiMethod constructor : constructors) {
            // variable must be initialized before its usage
            if (offset < constructor.getTextRange().getStartOffset()) continue;
            PsiCodeBlock body = constructor.getBody();
            if (body != null && variableDefinitelyAssignedIn(variable, body)) {
              return null;
            }
            // as a last chance, field may be initialized in this() call
            final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
            for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
              PsiMethod redirectedConstructor = redirectedConstructors.get(j);
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
          final PsiClassInitializer[] initializers = aClass.getInitializers();
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
        final ControlFlow controlFlow = getControlFlow(topBlock);
        codeBlockProblems = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
      }
      catch (AnalysisCanceledException | IndexNotReadyException e) {
        codeBlockProblems = Collections.emptyList();
      }
      uninitializedVarProblems.put(topBlock, codeBlockProblems);
    }
    if (codeBlockProblems.contains(expression)) {
      final String name = expression.getElement().getText();
      String description = JavaErrorMessages.message("variable.not.initialized", name);
      HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddVariableInitializerFix(variable));
      if (variable instanceof PsiField) {
        QuickFixAction.registerQuickFixAction(highlightInfo,
                                              QUICK_FIX_FACTORY.createModifierListFix(variable, PsiModifier.FINAL, false, false));
      }
      return highlightInfo;
    }

    return null;
  }

  private static boolean inInnerClass(@NotNull PsiElement psiElement, @Nullable PsiClass containingClass) {
    PsiElement element = psiElement;
    while (element != null) {
      if (element instanceof PsiClass) {
        final boolean innerClass = !psiElement.getManager().areElementsEquivalent(element, containingClass);
        if (innerClass) {
          if (element instanceof PsiAnonymousClass) {
            return !(PsiTreeUtil.isAncestor(((PsiAnonymousClass)element).getArgumentList(), psiElement, false) ||
                     insideClassInitialization(containingClass, (PsiClass)element));
          }
          final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLambdaExpression.class);
          return lambdaExpression == null || !insideClassInitialization(containingClass, (PsiClass)element);
        }
        return false;
      }
      element = element.getParent();
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
      final PsiElement parent = variable.getParent();
      if (parent == null) return false;
      final PsiElement declarationScope = parent.getParent();
      if (declarationScope == null) return false;
      Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
      return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
    }
    if (variable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)variable;
      return isAssigned(parameter);
    }
    return false;
  }


  @Nullable
  public static HighlightInfo checkFinalVariableMightAlreadyHaveBeenAssignedTo(@NotNull PsiVariable variable,
                                                                               @NotNull PsiReferenceExpression expression,
                                                                               @NotNull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems) {
    if (!PsiUtil.isAccessedForWriting(expression)) return null;

    final PsiElement scope = variable instanceof PsiField ? variable.getParent() :
                             variable.getParent() == null ? null : variable.getParent().getParent();
    PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
    if (codeBlock == null) return null;
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

    boolean alreadyAssigned = false;
    for (ControlFlowUtil.VariableInfo variableInfo : codeBlockProblems) {
      if (variableInfo.expression == expression) {
        alreadyAssigned = true;
        break;
      }
    }

    if (!alreadyAssigned) {
      if (!(variable instanceof PsiField)) return null;
      final PsiField field = (PsiField)variable;
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) return null;
      // field can get assigned in other field initializers
      final PsiField[] fields = aClass.getFields();
      boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
      for (PsiField psiField : fields) {
        PsiExpression initializer = psiField.getInitializer();
        if (psiField != field
            && psiField.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic
            && initializer != null
            && initializer != codeBlock
            && !variableDefinitelyNotAssignedIn(field, initializer)) {
          alreadyAssigned = true;
          break;
        }
      }

      if (!alreadyAssigned) {
        // field can get assigned in class initializers
        final PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
        if (enclosingConstructorOrInitializer == null
            || !aClass.getManager().areElementsEquivalent(enclosingConstructorOrInitializer.getContainingClass(), aClass)) {
          return null;
        }
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)
              == field.hasModifierProperty(PsiModifier.STATIC)) {
            final PsiCodeBlock body = initializer.getBody();
            if (body == codeBlock) return null;
            try {
              final ControlFlow controlFlow = getControlFlow(body);
              if (!ControlFlowUtil.isVariableDefinitelyNotAssigned(field, controlFlow)) {
                alreadyAssigned = true;
                break;
              }
            }
            catch (AnalysisCanceledException e) {
              // incomplete code
              return null;
            }
          }
        }
      }

      if (!alreadyAssigned
          && !field.hasModifierProperty(PsiModifier.STATIC)) {
        // then check if instance field already assigned in other constructor
        final PsiMethod ctr = codeBlock.getParent() instanceof PsiMethod ?
                              (PsiMethod)codeBlock.getParent() : null;
        // assignment to final field in several constructors threatens us only if these are linked (there is this() call in the beginning)
        final List<PsiMethod> redirectedConstructors = ctr != null && ctr.isConstructor() ? JavaHighlightUtil.getChainedConstructors(ctr) : null;
        for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
          PsiMethod redirectedConstructor = redirectedConstructors.get(j);
          PsiCodeBlock body = redirectedConstructor.getBody();
          if (body != null && variableDefinitelyAssignedIn(variable, body)) {
            alreadyAssigned = true;
            break;
          }
        }
      }
    }

    if (alreadyAssigned) {
      String description = JavaErrorMessages.message("variable.already.assigned", variable.getName());
      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo,
                                            QUICK_FIX_FACTORY.createModifierListFix(variable, PsiModifier.FINAL, false, false));
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createDeferFinalAssignmentFix(variable, expression));
      return highlightInfo;
    }

    return null;
  }

  @NotNull
  private static Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(@NotNull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
                                                                                          @NotNull PsiElement codeBlock) {
    Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = finalVarProblems.get(codeBlock);
    if (codeBlockProblems == null) {
      try {
        final ControlFlow controlFlow = getControlFlowNoConstantEvaluate(codeBlock);
        codeBlockProblems = ControlFlowUtil.getInitializedTwice(controlFlow);
      }
      catch (AnalysisCanceledException e) {
        codeBlockProblems = Collections.emptyList();
      }
      finalVarProblems.put(codeBlock, codeBlockProblems);
    }
    return codeBlockProblems;
  }


  @Nullable
  static HighlightInfo checkFinalVariableInitializedInLoop(@NotNull PsiReferenceExpression expression, @NotNull PsiElement resolved) {
    if (ControlFlowUtil.isVariableAssignedInLoop(expression, resolved)) {
      String description = JavaErrorMessages.message("variable.assigned.in.loop", ((PsiVariable)resolved).getName());
      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo,
                                            QUICK_FIX_FACTORY.createModifierListFix((PsiVariable)resolved, PsiModifier.FINAL, false, false));
      return highlightInfo;
    }
    return null;
  }


  @Nullable
  static HighlightInfo checkCannotWriteToFinal(@NotNull PsiExpression expression, @NotNull PsiFile containingFile) {
    PsiReferenceExpression reference = null;
    boolean readBeforeWrite = false;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiExpression left = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (left instanceof PsiReferenceExpression) {
        reference = (PsiReferenceExpression)left;
      }
      readBeforeWrite = assignmentExpression.getOperationTokenType() != JavaTokenType.EQ;
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPostfixExpression)expression).getOperand());
      final IElementType sign = ((PsiPostfixExpression)expression).getOperationTokenType();
      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
        reference = (PsiReferenceExpression)operand;
      }
      readBeforeWrite = true;
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPrefixExpression)expression).getOperand());
      final IElementType sign = ((PsiPrefixExpression)expression).getOperationTokenType();
      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
        reference = (PsiReferenceExpression)operand;
      }
      readBeforeWrite = true;
    }
    final PsiElement resolved = reference == null ? null : reference.resolve();
    PsiVariable variable = resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
    if (variable == null || !variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    final boolean canWrite = canWriteToFinal(variable, expression, reference, containingFile) && checkWriteToFinalInsideLambda(variable, reference) == null;
    if (readBeforeWrite || !canWrite) {
      final String name = variable.getName();
      String description = JavaErrorMessages.message(canWrite ? "variable.not.initialized" : "assignment.to.final.variable", name);
      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference.getTextRange()).descriptionAndTooltip(description).create();
      final PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, expression);
      if (innerClass == null || variable instanceof PsiField) {
        QuickFixAction.registerQuickFixAction(highlightInfo,
                                              QUICK_FIX_FACTORY.createModifierListFix(variable, PsiModifier.FINAL, false, false));
      }
      else {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, innerClass));
      }
      return highlightInfo;
    }

    return null;
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
      final PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null && isSameField(enclosingCtrOrInitializer, field, reference, containingFile);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = innerClass != null;
      if (isAccessedFromOtherClass) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSameField(@NotNull PsiMember enclosingCtrOrInitializer,
                                     @NotNull PsiField field,
                                     @NotNull PsiReferenceExpression reference,
                                     @NotNull PsiFile containingFile) {

    if (!containingFile.getManager().areElementsEquivalent(enclosingCtrOrInitializer.getContainingClass(), field.getContainingClass())) return false;
    PsiExpression qualifierExpression = reference.getQualifierExpression();
    return qualifierExpression == null || qualifierExpression instanceof PsiThisExpression;
  }


  @Nullable
  static HighlightInfo checkVariableMustBeFinal(@NotNull PsiVariable variable,
                                                @NotNull PsiJavaCodeReferenceElement context,
                                                @NotNull LanguageLevel languageLevel) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return null;
    final PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, context);
    if (innerClass instanceof PsiClass) {
      if (variable instanceof PsiParameter) {
        final PsiElement parent = variable.getParent();
        if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression &&
            notAccessedForWriting(variable, new LocalSearchScope(((PsiParameter)variable).getDeclarationScope()))) {
          return null;
        }
      }
      final boolean isToBeEffectivelyFinal = languageLevel.isAtLeast(LanguageLevel.JDK_1_8);
      if (isToBeEffectivelyFinal && isEffectivelyFinal(variable, innerClass, context)) {
        return null;
      }
      final String description = JavaErrorMessages.message(isToBeEffectivelyFinal ? "variable.must.be.final.or.effectively.final" : "variable.must.be.final", context.getText());

      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, innerClass));
      return highlightInfo;
    }
    return checkWriteToFinalInsideLambda(variable, context);
  }

  private static HighlightInfo checkWriteToFinalInsideLambda(@NotNull PsiVariable variable, @NotNull PsiJavaCodeReferenceElement context) {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
    if (lambdaExpression != null && !PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
      final PsiElement parent = variable.getParent();
      if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) {
        return null;
      }
      if (!isEffectivelyFinal(variable, lambdaExpression, context)) {
        String text = JavaErrorMessages.message("lambda.variable.must.be.final");
        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(text).create();
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, lambdaExpression));
        return ErrorFixExtensionPoint.registerFixes(highlightInfo, "lambda.variable.must.be.final");
      }
    }
    return null;
  }

  public static boolean isEffectivelyFinal(@NotNull PsiVariable variable, @NotNull PsiElement scope, @Nullable PsiJavaCodeReferenceElement context) {
    boolean effectivelyFinal;
    if (variable instanceof PsiParameter) {
      effectivelyFinal = notAccessedForWriting(variable, new LocalSearchScope(((PsiParameter)variable).getDeclarationScope()));
    }
    else {
      final ControlFlow controlFlow;
      try {
        PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, context);
        if (codeBlock == null) return true;
        controlFlow = getControlFlow(codeBlock);
      }
      catch (AnalysisCanceledException e) {
        return true;
      }

      final Collection<ControlFlowUtil.VariableInfo> initializedTwice = ControlFlowUtil.getInitializedTwice(controlFlow);
      effectivelyFinal = !initializedTwice.contains(new ControlFlowUtil.VariableInfo(variable, null));
      if (effectivelyFinal) {
        final List<PsiReferenceExpression> readBeforeWriteLocals = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
        for (PsiReferenceExpression expression : readBeforeWriteLocals) {
          if (expression.resolve() == variable) {
            return PsiUtil.isAccessedForReading(expression);
          }
        }
        effectivelyFinal = notAccessedForWriting(variable, new LocalSearchScope(scope));
        if (effectivelyFinal) {
          return ReferencesSearch.search(variable).forEach(ref -> {
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

  private static boolean notAccessedForWriting(@NotNull PsiVariable variable, @NotNull LocalSearchScope searchScope) {
    for (PsiReference reference : ReferencesSearch.search(variable, searchScope)) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static PsiElement getInnerClassVariableReferencedFrom(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    final PsiElement[] scope;
    if (variable instanceof PsiResourceVariable) {
      scope = ((PsiResourceVariable)variable).getDeclarationScope();
    }
    else if (variable instanceof PsiLocalVariable) {
      final PsiElement parent = variable.getParent();
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


  @Nullable
  static HighlightInfo checkInitializerCompleteNormally(@NotNull PsiClassInitializer initializer) {
    final PsiCodeBlock body = initializer.getBody();
    // unhandled exceptions already reported
    try {
      final ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
      final int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
      if (!BitUtil.isSet(completionReasons, ControlFlowUtil.NORMAL_COMPLETION_REASON)) {
        String description = JavaErrorMessages.message("initializer.must.be.able.to.complete.normally");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(body).descriptionAndTooltip(description).create();
      }
    }
    catch (AnalysisCanceledException e) {
      // incomplete code
    }
    return null;
  }
}
