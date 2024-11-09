// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DefUseInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;
  public boolean REPORT_PATTERN_VARIABLE = true;
  public boolean REPORT_FOR_EACH_PARAMETER = true;

  public static final String SHORT_NAME = "UnusedAssignment";

  @Override
  public void writeSettings(@NotNull Element node) {
    super.writeSettings(node);
    for (Element child : new ArrayList<>(node.getChildren())) {
      String name = child.getAttributeValue("name");
      String value = child.getAttributeValue("value");
      if (Set.of("REPORT_PATTERN_VARIABLE", "REPORT_FOR_EACH_PARAMETER").contains(name) &&
          "true".equals(value)) {
        node.removeContent(child);
      }
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        checkBody(method.getBody(), holder);
      }

      @Override
      public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
        checkBody(initializer.getBody(), holder);
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        checkBody(expression.getBody(), holder);
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        checkField(field, holder);
      }
    };
  }

  private void checkBody(PsiElement body, ProblemsHolder holder) {
    if (body == null) return;
    final Set<PsiVariable> usedVariables = new HashSet<>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      unusedDefs.sort(Comparator.comparingInt(o -> o.getContext().getTextOffset()));

      for (DefUseUtil.Info info : unusedDefs) {
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement || context instanceof PsiResourceVariable) {
          if (info.isRead() && REPORT_REDUNDANT_INITIALIZER) {
            PsiTypeElement typeElement = psiVariable.getTypeElement();
            if (typeElement == null || !typeElement.isInferredType()) {
              reportInitializerProblem(psiVariable, holder);
            }
          }
        }
        else if (context instanceof PsiAssignmentExpression) {
          PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
          if (parent == psiVariable) continue; // int x = x = 5; -- compilation error and reported as reassigned var
          if (parent instanceof PsiAssignmentExpression &&
              ((PsiAssignmentExpression)parent).getOperationTokenType() == JavaTokenType.EQ &&
              EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
                ((PsiAssignmentExpression)parent).getLExpression(), ((PsiAssignmentExpression)context).getLExpression())) {
            // x = x = 5; reported by "Variable is assigned to itself"
            continue;
          }
          reportAssignmentProblem((PsiAssignmentExpression)context, holder);
        }
        else if (context instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
                 context instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
          holder.registerProblem(context, JavaBundle.message("inspection.unused.assignment.problem.descriptor4"));
        }
        else if (REPORT_PATTERN_VARIABLE && psiVariable instanceof PsiPatternVariable &&
                 //case is covered with `Java | Declaration redundancy | Unused declaration`
                 info.isWriteOutsideDeclaration()) {
          holder.registerProblem(psiVariable.getNameIdentifier(), JavaBundle.message("inspection.unused.assignment.problem.descriptor5"));
        }
        else if (REPORT_FOR_EACH_PARAMETER && context instanceof PsiForeachStatement foreachStatement &&
                 foreachStatement.getIterationParameter() == psiVariable && psiVariable.getNameIdentifier() != null &&
                 //case is covered with `Java | Declaration redundancy | Unused declaration`
                 info.isWriteOutsideDeclaration()) {
          holder.registerProblem(psiVariable.getNameIdentifier(), JavaBundle.message("inspection.unused.assignment.problem.descriptor6"));
        }
      }
    }

    processFieldsViaDfa(body, holder);
  }

  private void processFieldsViaDfa(PsiElement body, ProblemsHolder holder) {
    DfaValueFactory factory = new DfaValueFactory(holder.getProject());
    var flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
    Set<DfaAnchor> variables = OverwrittenFieldAnalyzer.getOverwrittenFields(flow);
    for (DfaAnchor anchor : variables) {
      if (!(anchor instanceof JavaExpressionAnchor expressionAnchor)) continue;
      PsiElement expression = expressionAnchor.getExpression();
      if (expression instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
          expression instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
        holder.registerProblem(expression, JavaBundle.message("inspection.unused.assignment.problem.descriptor4"));
      }
      else if (expression instanceof PsiAssignmentExpression assignment) {
        reportAssignmentProblem(assignment, holder);
      }
    }
  }

  private static void reportInitializerProblem(PsiVariable psiVariable, ProblemsHolder holder) {
    holder.registerProblem(ObjectUtils.notNull(psiVariable.getInitializer(), psiVariable),
                           JavaBundle.message("inspection.unused.assignment.problem.descriptor2", psiVariable.getName()),
                           new RemoveInitializerFix());
  }

  private static void reportAssignmentProblem(PsiAssignmentExpression assignment,
                                              ProblemsHolder holder) {
    holder.registerProblem(assignment.getLExpression(),
                           JavaBundle.message("inspection.unused.assignment.problem.descriptor3",
                                              Objects.requireNonNull(assignment.getRExpression()).getText()),
                           new RemoveAssignmentFix()
    );
  }

  private void checkField(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
    if (field.hasModifierProperty(PsiModifier.FINAL)) return;
    final PsiClass psiClass = field.getContainingClass();
    if (psiClass == null) return;
    final PsiClassInitializer[] classInitializers = psiClass.getInitializers();
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiMethod[] constructors = !isStatic ? psiClass.getConstructors() : PsiMethod.EMPTY_ARRAY;
    final boolean fieldHasInitializer = field.hasInitializer() && PsiAugmentProvider.canTrustFieldInitializer(field);
    final int maxPossibleWritesCount = classInitializers.length + (constructors.length != 0 ? 1 : 0) + (fieldHasInitializer ? 1 : 0);
    if (maxPossibleWritesCount <= 1) return;

    final PsiClassInitializer initializerBeforeField = PsiTreeUtil.getPrevSiblingOfType(field, PsiClassInitializer.class);
    final List<FieldWrite> fieldWrites = new ArrayList<>(); // class initializers and field initializer in the program order

    if (fieldHasInitializer && initializerBeforeField == null) {
      fieldWrites.add(FieldWrite.createInitializer());
    }
    for (PsiClassInitializer classInitializer : classInitializers) {
      if (classInitializer.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        final List<PsiAssignmentExpression> assignments = collectAssignments(field, classInitializer);
        if (!assignments.isEmpty()) {
          boolean isDefinitely = HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, classInitializer.getBody());
          if (isDefinitely) {
            try {
              ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(classInitializer.getBody());
              if (ContainerUtil.exists(ControlFlowUtil.getReadBeforeWrite(flow),
                                       read -> (isStatic || ExpressionUtil.isEffectivelyUnqualified(read)) &&
                                               read.isReferenceTo(field))) {
                isDefinitely = false;
              }
            }
            catch (AnalysisCanceledException e) {
              // ignore
            }
          }
          fieldWrites.add(FieldWrite.createAssignments(isDefinitely, assignments));
        }
      }
      if (fieldHasInitializer && initializerBeforeField == classInitializer) {
        fieldWrites.add(FieldWrite.createInitializer());
      }
    }
    Collections.reverse(fieldWrites);

    boolean wasDefinitelyAssigned = isAssignedInAllConstructors(field, constructors);
    for (final FieldWrite fieldWrite : fieldWrites) {
      if (wasDefinitelyAssigned) {
        if (fieldWrite.isInitializer()) {
          if (REPORT_REDUNDANT_INITIALIZER) {
            reportInitializerProblem(field, holder);
          }
        }
        else {
          for (PsiAssignmentExpression assignment : fieldWrite.getAssignments()) {
            reportAssignmentProblem(assignment, holder);
          }
        }
      }
      else if (fieldWrite.isDefinitely()) {
        wasDefinitelyAssigned = true;
      }
    }
  }

  private static boolean isAssignedInAllConstructors(@NotNull PsiField field, PsiMethod @NotNull [] constructors) {
    if (constructors.length == 0 || field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    for (PsiMethod constructor : constructors) {
      if (!JavaHighlightUtil.getChainedConstructors(constructor).isEmpty()) continue;
      final PsiCodeBlock body = constructor.getBody();
      if (body == null || !HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        return false;
      }
      try {
        ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(body);
        if (ContainerUtil.exists(ControlFlowUtil.getReadBeforeWrite(flow),
                                 read -> ExpressionUtil.isEffectivelyUnqualified(read) && read.isReferenceTo(field))) {
          return false;
        }
        if (canBeUsedInCalledMethods(field, collectMethodsBeforeAssignment(field, flow))) {
          return false;
        }
      }
      catch (AnalysisCanceledException e) {
        return false;
      }
    }
    return true;
  }

  private static boolean canBeUsedInCalledMethods(PsiField field, List<PsiMethodCallExpression> expressions) {
    PsiClass containingClass = field.getContainingClass();
    PsiManager manager = field.getManager();
    for (PsiMethodCallExpression expression : expressions) {
      if (expression.getMethodExpression().resolve() instanceof PsiMethod method
          && !method.isConstructor()
          && !method.hasModifierProperty(PsiModifier.STATIC)
          && manager.areElementsEquivalent(method.getContainingClass(), containingClass)) {
        return true;
      }
      if (PsiTreeUtil.getChildrenOfType(expression.getArgumentList(), PsiThisExpression.class) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static List<PsiMethodCallExpression> collectMethodsBeforeAssignment(PsiField field, ControlFlow flow) {
    List<PsiMethodCallExpression> results = new ArrayList<>();
    PsiManager manager = field.getManager();
    List<ControlFlowUtil.ControlFlowEdge> edges = ControlFlowUtil.getEdges(flow, 0);
    Int2ObjectMap<List<ControlFlowUtil.ControlFlowEdge>> edgesFromStart = new Int2ObjectOpenHashMap<>();
    List<Instruction> instructions = flow.getInstructions();
    for (ControlFlowUtil.ControlFlowEdge edge : edges) {
      List<ControlFlowUtil.ControlFlowEdge> existedEdge = edgesFromStart.get(edge.myFrom);
      if (existedEdge != null) {
        existedEdge.add(edge);
      }
      else {
        List<ControlFlowUtil.ControlFlowEdge> newEdges = new ArrayList<>();
        newEdges.add(edge);
        edgesFromStart.put(edge.myFrom, newEdges);
      }
    }
    BitSet untilAssignment = new BitSet();
    ArrayDeque<Integer> unprocessedInstructions = new ArrayDeque<>();
    unprocessedInstructions.add(0);
    while (!unprocessedInstructions.isEmpty()) {
      int currentPoint = unprocessedInstructions.poll();
      if (instructions.size() <= currentPoint) {
        return results;
      }
      if (untilAssignment.get(currentPoint)) {
        continue;
      }
      Instruction instruction = instructions.get(currentPoint);
      if (instruction instanceof WriteVariableInstruction writeVariableInstruction &&
          manager.areElementsEquivalent(writeVariableInstruction.variable, field)) {
        continue;
      }
      untilAssignment.set(currentPoint);
      List<ControlFlowUtil.ControlFlowEdge> nextPoints = edgesFromStart.get(currentPoint);
      if (nextPoints != null) {
        unprocessedInstructions.addAll(ContainerUtil.map(nextPoints, t -> t.myTo));
      }
    }
    for (int index : untilAssignment.stream().toArray()) {
      if (flow.getElement(index) instanceof PsiMethodCallExpression methodCallExpression) {
        results.add(methodCallExpression);
      }
    }
    return results;
  }

  @NotNull
  private static List<PsiAssignmentExpression> collectAssignments(@NotNull PsiField field, @NotNull PsiClassInitializer classInitializer) {
    final List<PsiAssignmentExpression> assignmentExpressions = new ArrayList<>();
    classInitializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        final PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiJavaReference && ((PsiJavaReference)lExpression).isReferenceTo(field)) {
          final PsiExpression rExpression = expression.getRExpression();
          if (rExpression != null) {
            assignmentExpressions.add(expression);
          }
        }
        super.visitAssignmentExpression(expression);
      }
    });
    return assignmentExpressions;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("REPORT_REDUNDANT_INITIALIZER", JavaBundle.message("inspection.unused.assignment.option2")),
      OptPane.checkbox("REPORT_PREFIX_EXPRESSIONS", JavaBundle.message("inspection.unused.assignment.option")),
      OptPane.checkbox("REPORT_POSTFIX_EXPRESSIONS", JavaBundle.message("inspection.unused.assignment.option1")),
      OptPane.checkbox("REPORT_PATTERN_VARIABLE", JavaBundle.message("inspection.unused.assignment.option3")),
      OptPane.checkbox("REPORT_FOR_EACH_PARAMETER", JavaBundle.message("inspection.unused.assignment.option4"))
    );
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }


  private static final class FieldWrite {
    final boolean myDefinitely;
    final List<PsiAssignmentExpression> myAssignments;

    private FieldWrite(boolean definitely, List<PsiAssignmentExpression> assignments) {
      myDefinitely = definitely;
      myAssignments = assignments;
    }

    public boolean isDefinitely() {
      return myDefinitely;
    }

    public boolean isInitializer() {
      return myAssignments == null;
    }

    public List<PsiAssignmentExpression> getAssignments() {
      return myAssignments != null ? myAssignments : Collections.emptyList();
    }

    @NotNull
    public static FieldWrite createInitializer() {
      return new FieldWrite(true, null);
    }

    @NotNull
    public static FieldWrite createAssignments(boolean definitely, @NotNull List<PsiAssignmentExpression> assignmentExpressions) {
      return new FieldWrite(definitely, assignmentExpressions);
    }
  }
}