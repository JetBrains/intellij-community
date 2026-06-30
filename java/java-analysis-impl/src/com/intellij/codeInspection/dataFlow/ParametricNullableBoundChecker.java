// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Detects problems with a {@code @Nullable} upper bound (parametric nullness, e.g. {@code T extends @Nullable Object} in {@code @NullMarked} code).
 * Such {@code T} may be instantiated with a non-null type argument, so, for example, returning null from a {@code T}-returning
 * method or assigning null to a {@code T}-typed field is unsound.
 * <p>
 * The standard data flow run treats such a return type as nullable and emits no problem, so this checker re-runs the
 * analysis on a clone of the file in which the type parameter's {@code @Nullable} bound is replaced by a not-null
 * annotation (making {@code T} non-null), then maps the resulting nullable-return / null-assignment problems back onto the original PSI.
 */
final class ParametricNullableBoundChecker {
  private final @NotNull DataFlowInspectionBase myInspection;
  private final @NotNull ProblemsHolder myHolder;

  ParametricNullableBoundChecker(@NotNull DataFlowInspectionBase inspection, @NotNull ProblemsHolder holder) {
    myInspection = inspection;
    myHolder = holder;
  }

  void analyzeParametricNullableReturn(@NotNull PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return;
    if (!(method.getReturnType() instanceof PsiClassType classType)) return;
    BoundContext bound = getReportableBound(classType);
    if (bound == null) return;

    CloneFileContainer clone = getOrCreateNonNullBoundClone(bound.cloneContext());
    if (clone == null) return;
    CloneMethodContainer methodContainer = clone.methods.get(method);
    if (methodContainer == null) return;
    PsiMethod cloneMethod = methodContainer.cloneMethod.dereference();
    if (cloneMethod == null) return;
    PsiCodeBlock cloneBody = cloneMethod.getBody();
    if (cloneBody == null) return;

    List<NullabilityProblem<?>> cloneProblems = collectNullableReturnProblems(myInspection, cloneBody);
    if (cloneProblems.isEmpty()) return;

    List<NullabilityProblem<?>> originalProblems = new ArrayList<>();
    for (NullabilityProblem<?> cloneProblem : cloneProblems) {
      NullabilityProblem<PsiExpression> returnProblem = NullabilityProblemKind.nullableReturn.asMyProblem(cloneProblem);
      if (returnProblem == null) continue;
      PsiExpression originalAnchor = tryCast(mapByPath(returnProblem.getAnchor(), cloneBody, body), PsiExpression.class);
      if (originalAnchor == null) continue;
      PsiExpression cloneExpression = returnProblem.getDereferencedExpression();
      PsiExpression originalExpression =
        cloneExpression == null ? null : tryCast(mapByPath(cloneExpression, cloneBody, body), PsiExpression.class);
      NullabilityProblem<PsiExpression> originalProblem =
        NullabilityProblemKind.nullableReturn.problem(originalAnchor, originalExpression);
      if (originalProblem != null) originalProblems.add(originalProblem);
    }
    if (originalProblems.isEmpty()) return;

    myInspection.reportNullableReturnsProblems(new DataFlowInspectionBase.ProblemReporter(myHolder, body), originalProblems,
                                               Nullability.NOT_NULL, true, bound.boundAnnotation(),
                                               NullableNotNullManager.getInstance(method.getProject()));
  }

  void analyzeParametricField(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiTypeParameter) return;
    // Group candidate fields by the clone context: fields sharing a type parameter share one clone and one DFA run.
    Map<PsiElement, BoundContext> contextsByCloneAnchor = new LinkedHashMap<>();
    for (PsiField field : aClass.getFields()) {
      if (!field.isPhysical() || !(field.getType() instanceof PsiClassType classType)) continue;
      BoundContext bound = getReportableBound(classType);
      if (bound != null) contextsByCloneAnchor.putIfAbsent(bound.cloneContext(), bound);
    }
    if (contextsByCloneAnchor.isEmpty()) return;

    for (BoundContext bound : contextsByCloneAnchor.values()) {
      CloneFileContainer clone = getOrCreateNonNullBoundClone(bound.cloneContext());
      if (clone == null) continue;
      PsiClass cloneClass = PsiTreeUtil.findSameElementInCopy(aClass, clone.cloneFile);

      List<NullabilityProblem<?>> cloneProblems = collectAssigningToNotNullProblems(myInspection, cloneClass);
      if (cloneProblems.isEmpty()) continue;

      List<NullabilityProblem<?>> originalProblems = new ArrayList<>();
      for (NullabilityProblem<?> cloneProblem : cloneProblems) {
        NullabilityProblem<PsiExpression> assignProblem = NullabilityProblemKind.assigningToNotNull.asMyProblem(cloneProblem);
        if (assignProblem == null) continue;
        PsiExpression originalAnchor = tryCast(mapByPath(assignProblem.getAnchor(), cloneClass, aClass), PsiExpression.class);
        boolean assigns = originalAnchor != null && assignsToFieldOfType(originalAnchor, bound.typeParameter());
        if (originalAnchor == null) continue;
        // Keep only assignments to a field of this type parameter
        if (!assigns) continue;
        PsiExpression cloneExpression = assignProblem.getDereferencedExpression();
        PsiExpression originalExpression =
          cloneExpression == null ? null : tryCast(mapByPath(cloneExpression, cloneClass, aClass), PsiExpression.class);
        NullabilityProblem<PsiExpression> originalProblem =
          NullabilityProblemKind.assigningToNotNull.problem(originalAnchor, originalExpression);
        if (originalProblem != null) originalProblems.add(originalProblem);
      }
      if (originalProblems.isEmpty()) continue;

      myInspection.reportParametricAssignmentProblems(new DataFlowInspectionBase.ProblemReporter(myHolder, aClass), originalProblems);
    }
  }

  /**
   * Detects whether the given type (a field type or a method return type) is a type variable that may be instantiated as
   * non-null: a {@code @Nullable}/{@code @NullnessUnspecified} upper bound, or a plain unannotated type parameter.
   * Returns {@code null} when there is nothing to report (a not-null bound, or an opt-in-only case while the option is off).
   */
  private @Nullable BoundContext getReportableBound(@NotNull PsiClassType classType) {
    if (!(classType.resolve() instanceof PsiTypeParameter typeParameter)) return null;
    NullabilityAnnotationInfo info = classType.getNullability().toNullabilityAnnotationInfo();

    PsiAnnotation boundAnnotation;
    boolean optInOnly;
    if (info != null && info.isExtendedBounds()) {
      //T extends @Nullable Something
      //T extends @NullnessUnspecified Something
      Nullability nullability = info.getNullability();
      if (nullability == Nullability.NOT_NULL) return null;
      boundAnnotation = info.getAnnotation();
      if (!boundAnnotation.isPhysical() || PsiTreeUtil.getParentOfType(boundAnnotation, PsiTypeParameter.class) == null) {
        return null;
      }
      optInOnly = nullability != Nullability.NULLABLE; //@NullnessUnspecified, only for option
    }
    else if (info == null) {
      boundAnnotation = null;
      optInOnly = true; //no annotations, only for option
    }
    else {
      return null;
    }
    if (optInOnly && !myInspection.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS) return null;
    return new BoundContext(typeParameter, boundAnnotation);
  }

  /**
   * Runs data flow on the given (cloned) class over its class-initializer scope and each constructor, mirroring
   * {@link DataFlowInspectionBase}'s {@code visitClass}, and returns only the {@code assigningToNotNull} problems,
   * without reporting anything to a {@link ProblemsHolder}.
   */
  private static @NotNull List<NullabilityProblem<?>> collectAssigningToNotNullProblems(@NotNull DataFlowInspectionBase inspection,
                                                                                        @NotNull PsiClass cloneClass) {
    StandardDataFlowRunner runner =
      new StandardDataFlowRunner(cloneClass.getProject(), ThreeState.fromBoolean(inspection.IGNORE_ASSERT_STATEMENTS));
    List<NullabilityProblem<?>> result = new ArrayList<>();
    DataFlowInstructionVisitor classVisitor =
      collectFromScope(runner, cloneClass, Collections.singletonList(runner.createMemoryState()), result);
    if (classVisitor == null) return result;
    List<DfaMemoryState> endOfInitializerStates = classVisitor.getEndOfInitializerStates();
    for (PsiMethod constructor : cloneClass.getConstructors()) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      List<DfaMemoryState> initialStates =
        DataFlowInspectionBase.getConstructorInitialStates(cloneClass, constructor, runner, endOfInitializerStates);
      collectFromScope(runner, body, initialStates, result);
    }
    return result;
  }

  /**
   * Analyzes a single scope (the class-initializer scope or a constructor body) with the given runner and initial states,
   * appending any {@code assigningToNotNull} problems to {@code sink}. Returns the visitor (whose end-of-initializer states
   * the caller needs for constructor analysis) or {@code null} if the analysis did not succeed.
   */
  private static @Nullable DataFlowInstructionVisitor collectFromScope(@NotNull StandardDataFlowRunner runner,
                                                                       @NotNull PsiElement scope,
                                                                       @NotNull List<DfaMemoryState> initialStates,
                                                                       @NotNull List<NullabilityProblem<?>> sink) {
    DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor(false);
    ControlFlow flow = runner.buildFlow(scope);
    if (flow == null) return null;
    visitor.initInstanceOf(flow.getInstructions());
    RunnerResult result = runner.analyzeFlow(scope, visitor, initialStates, flow);
    if (result != RunnerResult.OK) {
      return null;
    }
    List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
    for (NullabilityProblem<?> problem : problems) {
      if (problem.getKind() == NullabilityProblemKind.assigningToNotNull) sink.add(problem);
    }
    return visitor;
  }

  /**
   * @return {@code true} if {@code anchor} is the value assigned to a field (a field initializer or an assignment to a
   * field reference) whose declared type resolves exactly to {@code typeParameter}.
   */
  private static boolean assignsToFieldOfType(@NotNull PsiExpression anchor, @NotNull PsiTypeParameter typeParameter) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    PsiField field = null;
    if (parent instanceof PsiField initializedField) {
      field = initializedField;
    }
    else if (parent instanceof PsiAssignmentExpression assignment &&
             assignment.getLExpression() instanceof PsiReferenceExpression ref &&
             ref.resolve() instanceof PsiField assignedField) {
      field = assignedField;
    }
    if (field == null) return false;
    PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(field.getType());
    return resolved != null && resolved.getManager().areElementsEquivalent(resolved, typeParameter);
  }

  /**
   * Runs data flow on the given (cloned) method body and returns only the {@code nullableReturn} problems,
   * without reporting anything to a {@link ProblemsHolder}.
   */
  private static @NotNull List<NullabilityProblem<?>> collectNullableReturnProblems(@NotNull DataFlowInspectionBase inspection,
                                                                                    @NotNull PsiCodeBlock cloneBody) {
    StandardDataFlowRunner runner =
      new StandardDataFlowRunner(cloneBody.getProject(), ThreeState.fromBoolean(inspection.IGNORE_ASSERT_STATEMENTS));
    // Report only definitely-nullable returns (a null literal or a @Nullable/union-null source). Treating unknown members as
    // nullable here (TREAT_UNKNOWN_MEMBERS_AS_NULLABLE) would turn merely-unspecified sources (@NullnessUnspecified) into
    // false mismatches, since those are "not enough information" rather than definitely null.
    DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor(false);
    ControlFlow flow = runner.buildFlow(cloneBody);
    if (flow == null) return Collections.emptyList();
    visitor.initInstanceOf(flow.getInstructions());
    RunnerResult result = runner.analyzeFlow(cloneBody, visitor, Collections.singletonList(runner.createMemoryState()), flow);
    if (result != RunnerResult.OK) return Collections.emptyList();
    List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
    return ContainerUtil.filter(problems, problem -> problem.getKind() == NullabilityProblemKind.nullableReturn);
  }

  private static @Nullable CloneFileContainer getOrCreateNonNullBoundClone(@NotNull PsiElement originalContext) {
    PsiFile originalFile = originalContext.getContainingFile();
    if (originalFile == null) return null;
    Map<PsiElement, CloneFileContainer> clones = CachedValuesManager.getCachedValue(originalFile, () ->
    {
      ConcurrentMap<PsiElement, @Nullable CloneFileContainer> cacheResult = ConcurrentFactoryMap.createMap(originalCacheContext -> {
        PsiFile clone = (PsiFile)originalFile.copy();
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(originalFile.getProject());
        if (!(clone instanceof PsiJavaFile javaFileClone)) return null;
        if (!(originalFile instanceof PsiJavaFile originalJavaFile)) return null;
        Map<PsiMethod, @Nullable CloneMethodContainer> methods = new HashMap<>();
        for (PsiClass originalClass : originalJavaFile.getClasses()) {
          if (!originalClass.isPhysical()) continue;
          for (PsiMethod originalMethod : originalClass.getMethods()) {
            if (!originalMethod.isPhysical() || !isBodyFullyPhysical(originalMethod)) continue;
            PsiMethod copyMethod = PsiTreeUtil.findSameElementInCopy(originalMethod, javaFileClone);

            SmartPsiElementPointer<PsiMethod> copyMethodPointer = smartPointerManager.createSmartPsiElementPointer(copyMethod);
            CloneMethodContainer methodContainer = new CloneMethodContainer(copyMethodPointer);
            methods.put(originalMethod, methodContainer);
          }
        }

        boolean changed = changeAnnotation(javaFileClone, originalCacheContext);
        if (!changed) return null;
        return new CloneFileContainer(javaFileClone, methods);
      });

      return CachedValueProvider.Result.create(cacheResult, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return clones.get(originalContext);
  }

  /**
   * @return {@code true} if the method has a body and every element of that body subtree is physical. A physical method
   * declaration alone is not enough: a body may still contain non-physical descendants (e.g. language injections or
   * augmented/light PSI), which the cloned-file analysis must not silently treat as physical.
   */
  private static boolean isBodyFullyPhysical(@NotNull PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    // processElements returns false as soon as it meets a non-physical element.
    return PsiTreeUtil.processElements(body, PsiElement::isPhysical);
  }

  private static boolean changeAnnotation(@NotNull PsiFile clone,
                                          @NotNull PsiElement originalContext) {
    Project project = clone.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    String notNull = AnnotationUtil.NOT_NULL;
    if (originalContext instanceof PsiAnnotation psiAnnotation && psiAnnotation.getQualifiedName() != null) {
      String notNullFrameworkAnnotation =
        manager.getNullabilityAnnotationInSameFramework(psiAnnotation.getQualifiedName(), Nullability.NOT_NULL);
      if (notNullFrameworkAnnotation != null) {
        notNull = notNullFrameworkAnnotation;
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (originalContext instanceof PsiAnnotation originalAnnotation) {
      // Replace the existing @Nullable / @NullnessUnspecified bound annotation with the not-null annotation
      // (by fully-qualified name to avoid touching imports in the clone).
      PsiAnnotation psiAnnotation = PsiTreeUtil.findSameElementInCopy(originalAnnotation, clone);
      PsiAnnotation replacement = factory.createAnnotationFromText("@" + notNull, psiAnnotation);
      psiAnnotation.replace(replacement);
      return true;
    }
    if (originalContext instanceof PsiTypeParameter originalTypeParameter) {
      PsiTypeParameter cloneTypeParameter = PsiTreeUtil.findSameElementInCopy(originalTypeParameter, clone);
      // Plain unannotated type parameter (no bound annotation to flip): rebuild it with a not-null upper bound,
      // preserving the original bound type(s). The not-null first bound forces the type variable non-null.
      String name = cloneTypeParameter.getName();
      if (name == null) return false;
      PsiClassType[] bounds = cloneTypeParameter.getExtendsListTypes();
      StringBuilder text = new StringBuilder().append(" extends java.lang. @").append(notNull).append(" Object");
      if (bounds.length != 0) {
        text.append(bounds[0].getCanonicalText());
        if (bounds.length > 1) {
          for (int i = 1; i < bounds.length; i++) {
            text.append(" & ").append(bounds[i].getCanonicalText());
          }
        }
      }
      Document document = clone.getFileDocument();
      document.insertString(cloneTypeParameter.getTextRange().getEndOffset(), text.toString());
      PsiDocumentManager.getInstance(clone.getProject()).commitDocument(document);
      return true;
    }
    return false;
  }

  /**
   * Maps an element from one PSI subtree to the structurally identical element in another subtree by child-index path.
   * Relies on {@code fromRoot} and {@code toRoot} being isomorphic (true here: the only edit between the clone and the
   * original are the bound annotation replacement, which is outside method bodies and preserves node counts).
   */
  private static @Nullable PsiElement mapByPath(@NotNull PsiElement element, @NotNull PsiElement fromRoot, @NotNull PsiElement toRoot) {
    List<Integer> path = new ArrayList<>();
    PsiElement current = element;
    while (current != fromRoot) {
      PsiElement parent = current.getParent();
      if (parent == null) return null;
      int index = 0;
      for (PsiElement child = parent.getFirstChild(); child != null && child != current; child = child.getNextSibling()) {
        index++;
      }
      path.add(index);
      current = parent;
    }
    PsiElement target = toRoot;
    for (int i = path.size() - 1; i >= 0; i--) {
      int index = path.get(i);
      PsiElement child = target.getFirstChild();
      for (int k = 0; k < index && child != null; k++) {
        child = child.getNextSibling();
      }
      if (child == null) return null;
      target = child;
    }
    return target;
  }

  private record CloneFileContainer(@NotNull PsiFile cloneFile,
                                    @NotNull Map<PsiMethod, @Nullable CloneMethodContainer> methods) {
  }

  private record CloneMethodContainer(@NotNull SmartPsiElementPointer<PsiMethod> cloneMethod) {
  }

  /** A type variable whose null assignment/return should be reported, together with the bound annotation (if any) to flip. */
  private record BoundContext(@NotNull PsiTypeParameter typeParameter, @Nullable PsiAnnotation boundAnnotation) {
    /** The element keying (and driving) the clone: the explicit bound annotation, or the plain type parameter itself. */
    private @NotNull PsiElement cloneContext() {
      return boundAnnotation != null ? boundAnnotation : typeParameter;
    }
  }
}
