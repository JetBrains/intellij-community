// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ForEachWithRecordPatternCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {

  private static final int MAX_LEVEL = 5;
  private static final int MAX_COMPONENT_COUNTS = 10;

  /**
   * @noinspection PublicField, FieldMayBeFinal
   */
  public int maxNotUsedComponentCounts = 0;

  /**
   * @noinspection PublicField, FieldMayBeFinal
   */
  public int maxComponentCounts = 5;

  /**
   * @noinspection PublicField, FieldMayBeFinal
   */
  public int maxLevel = 2;

  public boolean forceUseVar = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("maxLevel",
             InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.level.option"), 0, MAX_LEVEL),
      number("maxComponentCounts",
             InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.components.option"), 1,
             MAX_COMPONENT_COUNTS),
      number("maxNotUsedComponentCounts",
             InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.not.used.option"), 0,
             MAX_COMPONENT_COUNTS)
    );
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORD_PATTERNS_IN_FOR_EACH.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    boolean useVar = IntroduceVariableBase.createVarType() || forceUseVar;
    return new JavaElementVisitor() {
      private final Options options = new Options(maxNotUsedComponentCounts,
                                                  Math.min(maxComponentCounts, MAX_COMPONENT_COUNTS),
                                                  Math.min(maxLevel, MAX_LEVEL), useVar, isOnTheFly);

      @Override
      public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        ComponentContext context = createContext(statement, options);
        if (context == null) return;
        processComponentContext(context, this::highlight);
      }

      @Override
      public void visitForeachPatternStatement(@NotNull PsiForeachPatternStatement statement) {
        super.visitForeachPatternStatement(statement);
        boolean showAsInfo = false;
        if (options.maxLevel <= 0) {
          return;
        }
        PsiStatement foreachBody = statement.getBody();
        if (foreachBody == null) {
          return;
        }
        PsiPattern pattern = statement.getIterationPattern();
        if (!(pattern instanceof PsiDeconstructionPattern deconstructionPattern)) {
          return;
        }
        Deque<DeconstructionContext> queueToProcess = new ArrayDeque<>();
        queueToProcess.add(new DeconstructionContext(1, deconstructionPattern, statement));
        while (!queueToProcess.isEmpty()) {
          DeconstructionContext currentContext = queueToProcess.poll();
          int level = currentContext.level;
          if (level >= options.maxLevel + 1) {
            if (!options.isOnTheFly) {
              return;
            }
            showAsInfo = true;
          }
          PsiDeconstructionPattern currentDeconstruction = currentContext.pattern;
          PsiDeconstructionList list = currentDeconstruction.getDeconstructionList();
          for (PsiPattern component : list.getDeconstructionComponents()) {
            if (component instanceof PsiDeconstructionPattern newDeconstruction) {
              queueToProcess.add(new DeconstructionContext(level + 1, newDeconstruction, currentContext.foreachPatternStatement));
              continue;
            }
            if (component instanceof PsiTypeTestPattern typeTestPattern) {
              PsiTypeElement typeElement = typeTestPattern.getCheckType();
              PsiPatternVariable variable = typeTestPattern.getPatternVariable();
              if (typeElement == null || variable == null) {
                continue;
              }
              PsiType type = typeElement.getType();
              PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
              if (type instanceof PsiClassType referenceType) {
                substitutor = referenceType.resolveGenerics().getSubstitutor();
              }
              PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(type);
              if (parameterClass == null) {
                continue;
              }
              ShowInfoFolder showInfoFolder = new ShowInfoFolder();
              showInfoFolder.showInfo = showAsInfo;
              ComponentContext context =
                new ComponentContext(level, variable, parameterClass, substitutor, currentContext.foreachPatternStatement, options,
                                     showInfoFolder);
              processComponentContext(context, this::highlight);
            }
          }
        }
      }

      private void highlight(@NotNull ComponentContext context,
                             @NotNull Map<String, List<PsiElement>> components) {
        if (context.showInfoFolder.showInfo && !context.options.isOnTheFly) {
          return;
        }
        registerProblemForIdentifier(context.currentParameter, context, holder);
      }

      private void registerProblemForIdentifier(@NotNull PsiVariable variable,
                                                @NotNull ComponentContext context,
                                                @NotNull ProblemsHolder holder) {
        PsiIdentifier[] identifiers = PsiTreeUtil.getChildrenOfType(variable, PsiIdentifier.class);
        if (identifiers == null || identifiers.length != 1) {
          return;
        }
        PsiIdentifier identifier = identifiers[0];
        LocalQuickFix localQuickFix = getLocalQuickFix(context);
        if (context.showInfoFolder.showInfo) {
          holder.registerProblem(identifier,
                                 InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.message"),
                                 ProblemHighlightType.INFORMATION,
                                 localQuickFix);
        }
        else {
          List<LocalQuickFix> fixes = new ArrayList<>();
          fixes.add(localQuickFix);
          int length = context.recordClass.getRecordComponents().length;
          if (length > 1) {
            fixes.add(
              LocalQuickFix.from(new UpdateInspectionOptionFix(
                ForEachWithRecordPatternCanBeUsedInspection.this, "maxComponentCounts",
                InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.maximum.number.disabled", length),
                length - 1)));
          }
          Integer level = context.level;
          if (level != null && level > 0) {
            fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
              ForEachWithRecordPatternCanBeUsedInspection.this, "maxLevel",
              InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.maximum.depth.disabled", level), 
              level - 1)));
          }
          holder.registerProblem(identifier,
                                 InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.message"),
                                 fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      }

      @NotNull
      private LocalQuickFix getLocalQuickFix(@NotNull ComponentContext context) {
        return new ForEachWithRecordCanBeUsedFix(context.base, context.currentParameter);
      }
    };
  }

  private record DeconstructionContext(int level,
                                       @NotNull PsiDeconstructionPattern pattern,
                                       @NotNull PsiForeachPatternStatement foreachPatternStatement) {
  }

  private static void processComponentContext(@NotNull ComponentContext context,
                                              @NotNull ComponentContextProcessor processor) {
    @Nullable Integer level = context.level;
    PsiClass recordClass = context.recordClass;
    PsiStatement scope = context.base.getBody();
    boolean showAsInfo = false;
    if (!recordClass.isRecord() ||
        recordClass.getRecordComponents().length == 0) {
      return;
    }
    if ((level != null && level > context.options.maxLevel) ||
        recordClass.getRecordComponents().length > context.options.maxComponentCount) {
      if (!context.options.isOnTheFly) {
        return;
      }
      showAsInfo = true;
    }
    List<? extends PsiElement> references = VariableAccessUtils.getVariableReferences(context.currentParameter, scope);
    Map<String, List<PsiElement>> components = processReferences(references, context);
    if (components.isEmpty()) {
      return;
    }
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    if (recordComponents.length - components.size() > context.options.maxNotUsedComponentCount) {
      if (!context.options.isOnTheFly) {
        return;
      }
      showAsInfo = true;
    }
    context.showInfoFolder.showInfo = context.showInfoFolder.showInfo || showAsInfo;
    processor.accept(context, components);
  }

  @Nullable
  private static ComponentContext createContext(@NotNull PsiForeachStatement statement, @NotNull Options options) {
    PsiStatement body = statement.getBody();
    if (body == null) {
      return null;
    }
    PsiParameter parameter = statement.getIterationParameter();
    PsiType parameterType = parameter.getType();
    PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue == null) {
      return null;
    }
    PsiType iteratedType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
    if (iteratedType == null || iteratedType instanceof PsiClassType classType && classType.isRaw()) {
      return null;
    }
    if (!PsiTypesUtil.compareTypes(parameterType, iteratedType, false)) {
      return null;
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (parameterType instanceof PsiClassType referenceType) {
      substitutor = referenceType.resolveGenerics().getSubstitutor();
    }
    PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
    if (parameterClass == null) {
      return null;
    }
    return new ComponentContext(0, parameter, parameterClass, substitutor, statement, options, new ShowInfoFolder());
  }

  @NotNull
  private static Map<String, List<PsiElement>> processReferences(@NotNull List<? extends PsiElement> references,
                                                                 @NotNull ComponentContext context) {
    Map<String, List<PsiElement>> result = new HashMap<>();
    for (PsiElement reference : references) {
      PsiElement element = PsiUtil.skipParenthesizedExprUp(reference.getParent());
      if (element == null) {
        return Map.of();
      }
      if (!(element instanceof PsiReferenceExpression componentReferenceExpression)) {
        return Map.of();
      }
      PsiElement resolvedReferenceExpression = componentReferenceExpression.resolve();
      PsiRecordComponent component;
      if (resolvedReferenceExpression instanceof PsiMethod recordMethod && !recordMethod.isPhysical()) {
        component = JavaPsiRecordUtil.getRecordComponentForAccessor(recordMethod);
      }
      else if (resolvedReferenceExpression instanceof PsiField field && !field.isPhysical()) {
        component = JavaPsiRecordUtil.getComponentForField(field);
      }
      else {
        return Map.of();
      }
      if (component == null) {
        return Map.of();
      }
      PsiType substituted = context.classSubstitutor.substitute(component.getType());
      if (substituted == null || substituted instanceof PsiWildcardType || substituted instanceof PsiCapturedWildcardType) {
        return Map.of();
      }

      List<PsiElement> expressions = result.get(component.getName());
      PsiElement normalizedElement = normalizeElement(componentReferenceExpression, component, context);
      if (expressions == null) {
        List<PsiElement> referenceExpressions = new ArrayList<>();
        referenceExpressions.add(normalizedElement);
        result.put(component.getName(), referenceExpressions);
      }
      else {
        expressions.add(normalizedElement);
      }
    }
    return result;
  }

  @NotNull
  private static PsiElement normalizeElement(@NotNull PsiReferenceExpression reference,
                                             @NotNull PsiRecordComponent component,
                                             @NotNull ComponentContext context) {
    PsiElement expectedCallExpression = reference;
    if (PsiUtil.skipParenthesizedExprUp(expectedCallExpression.getParent()) instanceof PsiMethodCallExpression methodCallExpression) {
      expectedCallExpression = methodCallExpression;
    }
    PsiElement element = PsiUtil.skipParenthesizedExprUp(expectedCallExpression.getParent());
    if (element instanceof PsiVariable variable) {
      PsiTypeElement variableTypeElement = variable.getTypeElement();
      if (variableTypeElement == null) {
        return expectedCallExpression;
      }
      if (!variableTypeElement.isInferredType() && context.options.useVar) {
        return expectedCallExpression;
      }
      PsiType componentType = context.classSubstitutor.substitute(component.getType());
      if (PsiTypesUtil.compareTypes(componentType, variable.getType(), false) && variable.getAnnotations().length == 0) {
        return variable;
      }
    }
    return expectedCallExpression;
  }

  private class ForEachWithRecordCanBeUsedFix extends PsiUpdateModCommandQuickFix {
    private final SmartPsiElementPointer<PsiForeachStatementBase> myForEachStatement;
    private final SmartPsiElementPointer<PsiParameter> myParameter;

    private ForEachWithRecordCanBeUsedFix(@NotNull PsiForeachStatementBase forEachStatement,
                                          @NotNull PsiParameter parameter) {
      myForEachStatement = SmartPointerManager.createPointer(forEachStatement);
      myParameter = SmartPointerManager.createPointer(parameter);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiForeachStatementBase foreachStatementBase = updater.getWritable(myForEachStatement.getElement());
      PsiParameter parameter = updater.getWritable(myParameter.getElement());
      if (foreachStatementBase == null || parameter == null) {
        return;
      }
      Options options =
        new Options(maxNotUsedComponentCounts, maxComponentCounts, maxLevel, IntroduceVariableBase.createVarType() || forceUseVar, true);
      PsiType parameterType = parameter.getType();
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      if (parameterType instanceof PsiClassType referenceType) {
        substitutor = referenceType.resolveGenerics().getSubstitutor();
      }
      PsiClass parameterClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
      if (parameterClass == null) {
        return;
      }
      ComponentContext componentContext =
        new ComponentContext(null, parameter, parameterClass, substitutor, foreachStatementBase, options, new ShowInfoFolder());
      processComponentContext(componentContext, (currentContext, mapElements) -> {
        Map<String, List<String>> usedVariable = deleteVariablesAndMapToMethodCalls(mapElements, currentContext);
        PatternDefinition patternDefinition = createPatternDefinition(currentContext, usedVariable);
        if (patternDefinition == null) {
          return;
        }
        remapToPattern(patternDefinition, mapElements);
        replace(patternDefinition, currentContext);
      });
    }

    private static void replace(@NotNull PatternDefinition patternDefinition,
                                @NotNull ComponentContext context) {
      PsiForeachStatementBase foreachStatementBase = context.base;
      StringBuilder newStatementWithPattern = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      for (PsiElement element : foreachStatementBase.getChildren()) {
        if (PsiTreeUtil.isAncestor(element, context.currentParameter, false)) {
          addPatternToString(element, context.currentParameter, newStatementWithPattern, patternDefinition, commentTracker);
        }
        else {
          newStatementWithPattern.append(element.getText());
          commentTracker.markUnchanged(element);
        }
      }
      PsiReplacementUtil.replaceStatementAndShortenClassNames(context.base, newStatementWithPattern.toString(), commentTracker);
    }

    private static void addPatternToString(@NotNull PsiElement parent,
                                           @NotNull PsiParameter parameter,
                                           @NotNull StringBuilder patternStringBuilder,
                                           @NotNull PatternDefinition patternDefinition,
                                           @NotNull CommentTracker commentTracker) {
      if (parent == parameter) {
        patternStringBuilder.append(patternDefinition.text);
        return;
      }
      for (PsiElement child : parent.getChildren()) {
        if (PsiTreeUtil.isAncestor(child, parameter, false)) {
          addPatternToString(child, parameter, patternStringBuilder, patternDefinition, commentTracker);
        }
        else {
          patternStringBuilder.append(child.getText());
          commentTracker.markUnchanged(child);
        }
      }
    }

    @Nullable
    private static PatternDefinition createPatternDefinition(@NotNull ForEachWithRecordPatternCanBeUsedInspection.ComponentContext context,
                                                             @NotNull Map<String, List<String>> usedVariable) {
      PsiClass recordClass = context.recordClass;
      PsiStatement body = context.base.getBody();
      if (body == null) {
        return null;
      }
      Map<String, String> namesToComponents = new HashMap<>();
      List<String> variables = new ArrayList<>();
      for (PsiRecordComponent component : context.recordClass.getRecordComponents()) {
        String type = "var";
        if (!context.options.useVar) {
          PsiType substituted = context.classSubstitutor.substitute(component.getType());
          if (substituted == null) {
            return null;
          }
          type = substituted.getCanonicalText();
        }
        ArrayList<String> proposedNames = new ArrayList<>();
        proposedNames.add(component.getName());
        List<String> previouslyUsedNames = usedVariable.get(component.getName());
        if (previouslyUsedNames != null) {
          proposedNames.addAll(previouslyUsedNames);
        }
        VariableNameGenerator generator = new VariableNameGenerator(body, VariableKind.PARAMETER)
          .byName(ArrayUtil.toStringArray(proposedNames));
        String generatedName = generator.generate(true);
        variables.add(type + " " + generatedName);
        namesToComponents.put(component.getName(), generatedName);
      }
      StringJoiner joiner = new StringJoiner(", ", "(", ")");
      variables.forEach(pattern -> joiner.add(pattern));

      return new PatternDefinition(recordClass.getQualifiedName() + joiner, namesToComponents);
    }


    private static void remapToPattern(@NotNull PatternDefinition definition,
                                       @NotNull Map<String, List<PsiElement>> map) {
      for (Map.Entry<String, List<PsiElement>> entry : map.entrySet()) {
        String componentName = definition.namesToComponent.get(entry.getKey());
        for (PsiElement element : entry.getValue()) {
          CommentTracker tracker = new CommentTracker();
          if (element instanceof PsiExpression expression) {
            PsiReplacementUtil.replaceExpressionAndShorten(expression, componentName, tracker);
          }
        }
      }
    }

    @NotNull
    private static Map<String, List<String>> deleteVariablesAndMapToMethodCalls(@NotNull Map<String, List<PsiElement>> map,
                                                                                @NotNull ComponentContext context) {
      PsiParameter currentElement = context.currentParameter;
      String name = currentElement.getName();
      HashMap<String, List<String>> usedNames = new HashMap<>();
      for (Map.Entry<String, List<PsiElement>> componentReferences : map.entrySet()) {
        String method = name + "." + componentReferences.getKey() + "()";
        PsiElementFactory factory = PsiElementFactory.getInstance(currentElement.getProject());
        List<PsiElement> newElements = new ArrayList<>();
        for (PsiElement element : componentReferences.getValue()) {
          if (element instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiField) {
            CommentTracker tracker = new CommentTracker();
            newElements.add(PsiReplacementUtil.replaceExpressionAndShorten(referenceExpression, method, tracker));
            continue;
          }
          if (element instanceof PsiVariable currentVariable) {
            List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(currentVariable, context.base.getBody());
            for (PsiReferenceExpression reference : references) {
              PsiExpression newMethod = factory.createExpressionFromText(method, reference);
              PsiElement replacedElement = reference.replace(newMethod);
              newElements.add(replacedElement);
            }
            String variableName = currentVariable.getName();
            List<String> previousNames = usedNames.get(componentReferences.getKey());
            if (previousNames == null) {
              ArrayList<String> newNames = new ArrayList<>();
              newNames.add(variableName);
              usedNames.put(componentReferences.getKey(), newNames);
            }
            else {
              previousNames.add(variableName);
            }
            CommentTracker tracker = new CommentTracker();
            tracker.deleteAndRestoreComments(currentVariable);
          }
          else {
            newElements.add(element);
          }
        }
        componentReferences.getValue().clear();
        componentReferences.getValue().addAll(newElements);
      }
      return usedNames;
    }
  }

  private record PatternDefinition(@NotNull String text, @NotNull Map<String, String> namesToComponent) {
  }

  private interface ComponentContextProcessor
    extends BiConsumer<@NotNull ComponentContext, @NotNull Map<String, List<PsiElement>>> {
  }


  private record Options(int maxNotUsedComponentCount, int maxComponentCount, int maxLevel, boolean useVar, boolean isOnTheFly) {
  }

  private record ComponentContext(@Nullable Integer level,

                                  @NotNull PsiParameter currentParameter,
                                  @NotNull PsiClass recordClass,
                                  @NotNull PsiSubstitutor classSubstitutor,
                                  @NotNull PsiForeachStatementBase base,
                                  @NotNull Options options,
                                  @NotNull ShowInfoFolder showInfoFolder) {
  }

  private static class ShowInfoFolder {
    boolean showInfo;
  }
}


