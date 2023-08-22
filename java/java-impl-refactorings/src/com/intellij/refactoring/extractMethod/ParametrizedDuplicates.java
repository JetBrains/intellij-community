// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class ParametrizedDuplicates {
  private static final Logger LOG = Logger.getInstance(ParametrizedDuplicates.class);

  private final PsiElement[] myElements;
  private List<Match> myMatches;
  private List<ClusterOfUsages> myUsagesList;
  private PsiMethod myParametrizedMethod;
  private PsiMethodCallExpression myParametrizedCall;
  private VariableData[] myVariableDatum;

  private ParametrizedDuplicates(PsiElement @NotNull [] pattern, @NotNull ExtractMethodProcessor originalProcessor) {
    PsiElement[] filteredPattern = getFilteredElements(pattern);
    PsiElement firstElement = filteredPattern.length != 0 ? filteredPattern[0] : null;
    if (firstElement instanceof PsiStatement) {
      PsiElement[] copy = copyElements(pattern);
      myElements = wrapWithCodeBlock(copy, originalProcessor.getInputVariables());
    }
    else if (firstElement instanceof PsiExpression) {
      PsiElement[] copy = copyElements(pattern);
      PsiExpression wrapped = wrapExpressionWithCodeBlock(copy, originalProcessor);
      myElements = wrapped != null ? new PsiElement[]{wrapped} : PsiElement.EMPTY_ARRAY;
    }
    else {
      myElements = PsiElement.EMPTY_ARRAY;
    }
  }

  private static PsiElement[] copyElements(PsiElement @NotNull [] pattern) {
    Project project = pattern[0].getProject();
    return IntroduceParameterHandler.getElementsInCopy(project, pattern[0].getContainingFile(), pattern, false);
  }

  @Nullable
  public static ParametrizedDuplicates findDuplicates(@NotNull ExtractMethodProcessor originalProcessor,
                                                      @NotNull DuplicatesFinder.MatchType matchType,
                                                      @Nullable Set<? extends TextRange> textRanges) {
    DuplicatesFinder finder = createDuplicatesFinder(originalProcessor, matchType, textRanges);
    if (finder == null) {
      return null;
    }
    List<Match> matches = finder.findDuplicates(originalProcessor.myTargetClass);
    matches = filterNestedSubexpressions(matches);
    if (matches.isEmpty()) {
      return null;
    }

    Map<PsiExpression, String> predefinedNames = foldParameters(originalProcessor, matches);

    PsiElement[] pattern = originalProcessor.myElements;
    ParametrizedDuplicates duplicates = new ParametrizedDuplicates(pattern, originalProcessor);
    if (!duplicates.initMatches(pattern, matches)) {
      return null;
    }

    if (!duplicates.extract(originalProcessor, predefinedNames)) {
      return null;
    }
    return duplicates;
  }

  @NotNull
  private static Map<PsiExpression, String> foldParameters(ExtractMethodProcessor originalProcessor, List<Match> matches) {
    if (matches.isEmpty() || !originalProcessor.getInputVariables().isFoldable()) {
      return Collections.emptyMap();
    }

    // As folded parameters don't work along with extracted parameters we need to apply the finder again to actually fold the parameters
    DuplicatesFinder finder = createDuplicatesFinder(originalProcessor, DuplicatesFinder.MatchType.FOLDED, null);
    if (finder == null) {
      return Collections.emptyMap();
    }
    Map<Match, Match> foldedMatches = new HashMap<>();
    Map<DuplicatesFinder.Parameter, VariableData> parametersToFold = new LinkedHashMap<>();
    for (VariableData data : originalProcessor.getInputVariables().getInputVariables()) {
      parametersToFold.put(new DuplicatesFinder.Parameter(data.variable, data.type, true), data);
    }

    for (Match match : matches) {
      Match foldedMatch = finder.isDuplicate(match.getMatchStart(), false);
      LOG.assertTrue(foldedMatch != null, "folded match should exist");
      LOG.assertTrue(match.getMatchStart() == foldedMatch.getMatchStart() &&
                     match.getMatchEnd() == foldedMatch.getMatchEnd(), "folded match range should be the same");
      foldedMatches.put(match, foldedMatch);

      parametersToFold.keySet().removeIf(parameter -> !canFoldParameter(match, foldedMatch, parameter));
    }
    if (parametersToFold.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<PsiExpression, String> predefinedNames = new HashMap<>();
    for (Match match : matches) {
      Match foldedMatch = foldedMatches.get(match);
      LOG.assertTrue(foldedMatch != null, "folded match");

      for (Map.Entry<DuplicatesFinder.Parameter, VariableData> entry : parametersToFold.entrySet()) {
        DuplicatesFinder.Parameter parameter = entry.getKey();
        VariableData variableData = entry.getValue();
        List<Pair.NonNull<PsiExpression, PsiExpression>> expressionMappings = foldedMatch.getFoldedExpressionMappings(parameter);
        LOG.assertTrue(!ContainerUtil.isEmpty(expressionMappings), "foldedExpressionMappings can't be empty");
        PsiType type = parameter.getType();

        ExtractedParameter extractedParameter = null;
        for (Pair.NonNull<PsiExpression, PsiExpression> expressionMapping : expressionMappings) {
          PsiExpression patternExpression = expressionMapping.getFirst();
          ExtractableExpressionPart patternPart = ExtractableExpressionPart.fromUsage(patternExpression, type);
          if (extractedParameter == null) {
            PsiExpression candidateExpression = expressionMapping.getSecond();
            ExtractableExpressionPart candidatePart = ExtractableExpressionPart.fromUsage(candidateExpression, type);
            extractedParameter = new ExtractedParameter(patternPart, candidatePart, type);
          }
          else {
            extractedParameter.addUsages(patternPart);
          }
          predefinedNames.put(patternExpression, variableData.name);
        }
        LOG.assertTrue(extractedParameter != null, "extractedParameter can't be null");
        match.getExtractedParameters().add(extractedParameter);
      }
    }

    return predefinedNames;
  }

  private static boolean canFoldParameter(Match match, Match foldedMatch, DuplicatesFinder.Parameter parameter) {
    List<Pair.NonNull<PsiExpression, PsiExpression>> expressionMappings = foldedMatch.getFoldedExpressionMappings(parameter);
    if (ContainerUtil.isEmpty(expressionMappings)) {
      return false;
    }
    // Extracted parameters and folded parameters shouldn't overlap
    for (Pair.NonNull<PsiExpression, PsiExpression> expressionMapping : expressionMappings) {
      PsiExpression patternExpression = expressionMapping.getFirst();
      for (ExtractedParameter extractedParameter : match.getExtractedParameters()) {
        for (PsiExpression extractedUsage : extractedParameter.myPatternUsages) {
          if (PsiTreeUtil.isAncestor(patternExpression, extractedUsage, false) ||
              PsiTreeUtil.isAncestor(extractedUsage, patternExpression, false)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @Nullable
  private static DuplicatesFinder createDuplicatesFinder(@NotNull ExtractMethodProcessor processor,
                                                         @NotNull DuplicatesFinder.MatchType matchType,
                                                         @Nullable Set<? extends TextRange> textRanges) {
    PsiElement[] elements = getFilteredElements(processor.myElements);
    if (elements.length == 0) {
      return null;
    }
    Set<PsiVariable> effectivelyLocal = processor.getEffectivelyLocalVariables();

    InputVariables inputVariables = matchType == DuplicatesFinder.MatchType.PARAMETRIZED
                                    ? processor.myInputVariables.copyWithoutFolding() : processor.myInputVariables;
    ReturnValue returnValue = processor.myOutputVariable != null ? new VariableReturnValue(processor.myOutputVariable) : null;
    return new DuplicatesFinder(elements, inputVariables, returnValue,
                                Collections.emptyList(), matchType, effectivelyLocal, textRanges);
  }

  @NotNull
  PsiMethod replaceMethod(@NotNull PsiMethod originalMethod) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalMethod.getProject());
    String text = myParametrizedMethod.getText();
    PsiMethod method = factory.createMethodFromText(text, originalMethod.getParent());
    return (PsiMethod)originalMethod.replace(method);
  }

  @NotNull
  PsiMethodCallExpression replaceCall(@NotNull PsiMethodCallExpression originalCall) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalCall.getProject());
    String text = myParametrizedCall.getText();
    PsiMethodCallExpression call = (PsiMethodCallExpression)factory.createExpressionFromText(text, originalCall.getParent());
    return (PsiMethodCallExpression)originalCall.replace(call);
  }

  private boolean initMatches(PsiElement @NotNull [] pattern, @NotNull List<Match> matches) {
    if (myElements.length == 0) {
      return false;
    }

    myUsagesList = new ArrayList<>();
    Map<PsiExpression, ClusterOfUsages> usagesMap = new HashMap<>();
    Set<Match> badMatches = new HashSet<>();
    for (Match match : matches) {
      List<ClusterOfUsages> usagesInMatch = getUsagesInMatch(usagesMap, match);
      if (usagesInMatch == null) {
        badMatches.add(match);
        continue;
      }
      for (ClusterOfUsages usages : usagesInMatch) {
        myUsagesList.add(usages);
        for (PsiExpression expression : usages.myPatterns) {
          usagesMap.put(expression, usages);
        }
      }
    }

    if (!badMatches.isEmpty()) {
      matches = new ArrayList<>(matches);
      matches.removeAll(badMatches);
    }
    myMatches = matches;
    if (myMatches.isEmpty()) {
      return false;
    }

    Map<Match, Map<PsiExpression, PsiExpression>> expressionsMapping = new HashMap<>();
    for (ClusterOfUsages usages : myUsagesList) {
      for (Match match : myMatches) {
        ExtractedParameter parameter = usages.getParameter(match);
        if (parameter == null) {
          Map<PsiExpression, PsiExpression> expressions =
            expressionsMapping.computeIfAbsent(match, unused -> {
              Map<PsiExpression, PsiExpression> result = new HashMap<>();
              collectCopyMapping(pattern, match.getMatchElements(), usagesMap.keySet()::contains, result::put, (unused1, unused2) -> {});
              return result;
            });
          PsiExpression candidateUsage = usages.myPatterns.stream().map(expressions::get).findAny().orElse(null);
          LOG.assertTrue(candidateUsage != null, "candidateUsage shouldn't be null");

          ExtractedParameter fromParameter = usages.myParameter;
          parameter = fromParameter.copyWithCandidateUsage(candidateUsage);
          match.addExtractedParameter(parameter);
          usages.putParameter(match, parameter);
        }
      }
    }

    mergeDuplicateUsages(myUsagesList, myMatches);
    myUsagesList.sort(Comparator.comparing(usages -> usages.myFirstOffset));
    return true;
  }

  private static void mergeDuplicateUsages(@NotNull List<? extends ClusterOfUsages> usagesList, @NotNull List<Match> matches) {
    Set<ClusterOfUsages> duplicateUsages = new HashSet<>();
    for (int i = 0; i < usagesList.size(); i++) {
      ClusterOfUsages usages = usagesList.get(i);
      if (duplicateUsages.contains(usages)) continue;

      for (int j = i + 1; j < usagesList.size(); j++) {
        ClusterOfUsages otherUsages = usagesList.get(j);

        if (usages.isEquivalent(otherUsages, matches)) {
          for (Match match : matches) {
            ExtractedParameter parameter = usages.getParameter(match);
            ExtractedParameter otherParameter = otherUsages.getParameter(match);
            if (parameter != null && otherParameter != null) {
              parameter.addUsages(otherParameter.myPattern);
              match.getExtractedParameters().remove(otherParameter);
            }
          }
          duplicateUsages.add(otherUsages);
        }
      }
    }
    usagesList.removeAll(duplicateUsages);
  }

  private static List<Match> filterNestedSubexpressions(List<Match> matches) {
    Map<PsiExpression, Set<Match>> patternUsages = new HashMap<>();
    for (Match match : matches) {
      for (ExtractedParameter parameter : match.getExtractedParameters()) {
        for (PsiExpression patternUsage : parameter.myPatternUsages) {
          patternUsages.computeIfAbsent(patternUsage, k -> new HashSet<>()).add(match);
        }
      }
    }

    Set<Match> badMatches = new HashSet<>();
    for (Map.Entry<PsiExpression, Set<Match>> entry : patternUsages.entrySet()) {
      PsiExpression patternUsage = entry.getKey();
      Set<Match> patternMatches = entry.getValue();
      for (PsiExpression maybeNestedUsage : patternUsages.keySet()) {
        if (patternUsage == maybeNestedUsage) {
          continue;
        }
        if (PsiTreeUtil.isAncestor(patternUsage, maybeNestedUsage, true)) {
          badMatches.addAll(patternMatches);
          break;
        }
      }
    }

    if (!badMatches.isEmpty()) {
      matches = new ArrayList<>(matches);
      matches.removeAll(badMatches);
    }
    return matches;
  }

  @Nullable
  private static List<ClusterOfUsages> getUsagesInMatch(@NotNull Map<PsiExpression, ClusterOfUsages> usagesMap, @NotNull Match match) {
    List<ClusterOfUsages> result = new ArrayList<>();
    List<ExtractedParameter> parameters = match.getExtractedParameters();
    for (ExtractedParameter parameter : parameters) {
      ClusterOfUsages usages = usagesMap.get(parameter.myPattern.getUsage());
      if (usages != null && !usages.arePatternsEquivalent(parameter) ||
          usages == null && ClusterOfUsages.isPatternPresent(usagesMap, parameter)) {
        return null;
      }
      if (usages == null) {
        result.add(usages = new ClusterOfUsages(parameter));
      }
      usages.putParameter(match, parameter);
    }
    return result;
  }

  private boolean extract(@NotNull ExtractMethodProcessor originalProcessor, @NotNull Map<PsiExpression, String> predefinedNames) {
    Map<PsiExpression, PsiExpression> expressionsMapping = new HashMap<>();
    Map<PsiVariable, PsiVariable> variablesMapping = new HashMap<>();
    collectCopyMapping(originalProcessor.myElements, myElements, myUsagesList, expressionsMapping, variablesMapping);

    Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations =
      createParameterDeclarations(originalProcessor, expressionsMapping, predefinedNames);
    putMatchParameters(parameterDeclarations);

    JavaDuplicatesExtractMethodProcessor parametrizedProcessor =
      new JavaDuplicatesExtractMethodProcessor(myElements, ExtractMethodHandler.getRefactoringName());
    if (!parametrizedProcessor.prepare(false)) {
      return false;
    }
    parametrizedProcessor.applyFrom(originalProcessor, variablesMapping);
    parametrizedProcessor.doExtract();
    myParametrizedMethod = parametrizedProcessor.getExtractedMethod();
    myParametrizedCall = parametrizedProcessor.getMethodCall();
    myVariableDatum = unmapVariableData(parametrizedProcessor.myVariableDatum, variablesMapping);
    replaceArguments(parameterDeclarations, myParametrizedCall);

    return true;
  }

  private static VariableData @NotNull [] unmapVariableData(VariableData @NotNull [] variableDatum,
                                                            @NotNull Map<PsiVariable, PsiVariable> variablesMapping) {
    Map<PsiVariable, PsiVariable> reverseMapping = ContainerUtil.reverseMap(variablesMapping);
    return StreamEx.of(variableDatum)
                   .map(data -> data.substitute(reverseMapping.get(data.variable)))
                   .toArray(VariableData[]::new);
  }

  private static void replaceArguments(@NotNull Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations,
                                       @NotNull PsiMethodCallExpression parametrizedCall) {
    PsiExpression[] arguments = parametrizedCall.getArgumentList().getExpressions();
    for (PsiExpression argument : arguments) {
      if (argument instanceof PsiReferenceExpression) {
        PsiElement resolved = ((PsiReferenceExpression)argument).resolve();
        if (resolved instanceof PsiLocalVariable && parameterDeclarations.containsKey(resolved)) {
          PsiExpression initializer = ((PsiLocalVariable)resolved).getInitializer();
          if (initializer != null) {
            argument.replace(initializer);
          }
        }
      }
    }
  }

  private void putMatchParameters(@NotNull Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations) {
    Map<PsiExpression, PsiLocalVariable> patternUsageToParameter = new HashMap<>();
    for (Map.Entry<PsiLocalVariable, ClusterOfUsages> entry : parameterDeclarations.entrySet()) {
      PsiExpression usage = entry.getValue().myParameter.myPattern.getUsage();
      patternUsageToParameter.put(usage, entry.getKey());
    }

    for (Match match : myMatches) {
      List<ExtractedParameter> matchedParameters = match.getExtractedParameters();
      for (ExtractedParameter matchedParameter : matchedParameters) {
        PsiLocalVariable localVariable = patternUsageToParameter.get(matchedParameter.myPattern.getUsage());
        LOG.assertTrue(localVariable != null, "match local variable");
        DuplicatesFinder.Parameter parameter = new DuplicatesFinder.Parameter(localVariable, matchedParameter.myType);
        boolean ok = match.putParameter(parameter, matchedParameter.myCandidate.getUsage());
        LOG.assertTrue(ok, "put match parameter");
      }
    }
  }

  public PsiMethod getParametrizedMethod() {
    return myParametrizedMethod;
  }

  public PsiMethodCallExpression getParametrizedCall() {
    return myParametrizedCall;
  }

  VariableData[] getVariableDatum() {
    return myVariableDatum;
  }

  public int getSize() {
    return myMatches != null ? myMatches.size() : 0;
  }

  public List<Match> getDuplicates() {
    return myMatches;
  }

  boolean isEmpty() {
    return ContainerUtil.isEmpty(myMatches);
  }

  private static PsiElement @NotNull [] wrapWithCodeBlock(PsiElement @NotNull [] elements, @NotNull InputVariables inputVariables) {
    PsiElement fragmentStart = elements[0];
    PsiElement fragmentEnd = elements[elements.length - 1];
    List<ReusedLocalVariable> reusedLocalVariables =
      ReusedLocalVariablesFinder.findReusedLocalVariables(fragmentStart, fragmentEnd, Collections.emptySet(), inputVariables);

    PsiElement parent = fragmentStart.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(fragmentStart.getProject());
    PsiBlockStatement statement = (PsiBlockStatement)factory.createStatementFromText("{}", parent);
    statement.getCodeBlock().addRange(fragmentStart, fragmentEnd);
    statement = (PsiBlockStatement)parent.addBefore(statement, fragmentStart);
    parent.deleteChildRange(fragmentStart, fragmentEnd);

    PsiElement[] elementsInBlock = trimBracesAndWhitespaces(statement.getCodeBlock());

    declareReusedLocalVariables(reusedLocalVariables, statement, factory);
    return elementsInBlock;
  }

  private static PsiElement @NotNull [] trimBracesAndWhitespaces(@NotNull PsiCodeBlock codeBlock) {
    PsiElement[] elements = codeBlock.getChildren();
    int start = 1;
    while (start < elements.length && elements[start] instanceof PsiWhiteSpace) {
      start++;
    }
    int end = elements.length - 1;
    while (end > 0 && elements[end - 1] instanceof PsiWhiteSpace) {
      end--;
    }
    LOG.assertTrue(start < end, "wrapper block length is too small");
    return Arrays.copyOfRange(elements, start, end);
  }

  private static void declareReusedLocalVariables(@NotNull List<? extends ReusedLocalVariable> reusedLocalVariables,
                                                  @NotNull PsiBlockStatement statement,
                                                  @NotNull PsiElementFactory factory) {
    PsiElement parent = statement.getParent();
    PsiCodeBlock codeBlock = statement.getCodeBlock();
    PsiStatement addAfter = statement;
    for (ReusedLocalVariable variable : reusedLocalVariables) {
      if (variable.reuseValue()) {
        PsiStatement declarationBefore = factory.createStatementFromText(variable.getTempDeclarationText(), codeBlock.getRBrace());
        parent.addBefore(declarationBefore, statement);

        PsiStatement assignment = factory.createStatementFromText(variable.getAssignmentText(), codeBlock.getRBrace());
        codeBlock.addBefore(assignment, codeBlock.getRBrace());
      }
      PsiStatement declarationAfter = factory.createStatementFromText(variable.getDeclarationText(), statement);
      parent.addAfter(declarationAfter, addAfter);
      addAfter = declarationAfter;
    }
  }

  @Nullable
  private static PsiExpression wrapExpressionWithCodeBlock(PsiElement @NotNull [] copy,
                                                           @NotNull ExtractMethodProcessor originalProcessor) {
    if (copy.length != 1 || !(copy[0] instanceof PsiExpression expression)) return null;

    PsiType type = expression.getType();
    if (type == null || PsiTypes.nullType().equals(type)) return null;

    PsiElement parent = expression.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (parentClass == null) return null;

    PsiElement parentClassStart = parentClass.getLBrace();
    if (parentClassStart == null) return null;

    // It's syntactically correct to write "new Object() {void foo(){}}.foo()" - see JLS 15.9.5
    @NonNls String wrapperBodyText = (PsiTypes.voidType().equals(type) ? "" : "return ") + expression.getText() + ";";
    String wrapperClassImmediateCallText = "new " + CommonClassNames.JAVA_LANG_OBJECT + "() { " +
                                           type.getCanonicalText() + " wrapperMethod() {" + wrapperBodyText + "} " +
                                           "}.wrapperMethod()";
    PsiExpression wrapperClassImmediateCall = factory.createExpressionFromText(wrapperClassImmediateCallText, parent);
    wrapperClassImmediateCall = (PsiExpression)expression.replace(wrapperClassImmediateCall);
    PsiMethod method = PsiTreeUtil.findChildOfType(wrapperClassImmediateCall, PsiMethod.class);
    LOG.assertTrue(method != null, "wrapper class method is null");

    PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null, "wrapper class method's body is null");

    PsiStatement[] statements = body.getStatements();
    LOG.assertTrue(statements.length == 1, "wrapper class method's body statement count");
    PsiStatement bodyStatement = statements[0];

    Set<PsiVariable> effectivelyLocal = originalProcessor.getEffectivelyLocalVariables();
    for (PsiVariable variable : effectivelyLocal) {
      String name = variable.getName();
      LOG.assertTrue(name != null, "effectively local variable's name is null");
      PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(name, variable.getType(), null);
      body.addBefore(declaration, bodyStatement);
    }

    PsiExpression wrapped = null;
    if (PsiTypes.voidType().equals(type) && bodyStatement instanceof PsiExpressionStatement) {
      wrapped = ((PsiExpressionStatement)bodyStatement).getExpression();
    }
    else if (bodyStatement instanceof PsiReturnStatement) {
      wrapped = ((PsiReturnStatement)bodyStatement).getReturnValue();
    }
    else {
      LOG.error("Unexpected statement in expression code block " + bodyStatement);
    }
    if (wrapped != null) {
      // this key is not copyable so replace() doesn't preserve it - have to do it here
      wrapped.putUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL, expression.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL));
    }
    return wrapped;
  }

  @NotNull
  private Map<PsiLocalVariable, ClusterOfUsages> createParameterDeclarations(@NotNull ExtractMethodProcessor originalProcessor,
                                                                             @NotNull Map<PsiExpression, PsiExpression> expressionsMapping,
                                                                             @NotNull Map<PsiExpression, String> predefinedNames) {

    Project project = myElements[0].getProject();
    Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations = new HashMap<>();
    UniqueNameGenerator generator = originalProcessor.getParameterNameGenerator(myElements[0]);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiStatement statement =
      myElements[0] instanceof PsiStatement ? (PsiStatement)myElements[0] : PsiTreeUtil.getParentOfType(myElements[0], PsiStatement.class);
    LOG.assertTrue(statement != null, "first statement is null");
    PsiElement parent = statement.getParent();
    LOG.assertTrue(parent instanceof PsiCodeBlock, "first statement's parent isn't a code block");

    for (ClusterOfUsages usages : myUsagesList) {
      ExtractedParameter parameter = usages.myParameter;
      PsiExpression patternUsage = parameter.myPattern.getUsage();
      String initializerText = patternUsage.getText();
      PsiExpression initializer = factory.createExpressionFromText(initializerText, parent);
      String predefinedName = predefinedNames.get(patternUsage);
      final SuggestedNameInfo info =
        JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, predefinedName, initializer, null);
      final String parameterName = generator.generateUniqueName(info.names.length > 0 ? info.names[0] : "p");

      String declarationText = parameter.getLocalVariableTypeText() + " " + parameterName + " = " + initializerText + ";";
      PsiDeclarationStatement paramDeclaration = (PsiDeclarationStatement)factory.createStatementFromText(declarationText, parent);
      paramDeclaration = (PsiDeclarationStatement)parent.addBefore(paramDeclaration, statement);
      PsiLocalVariable localVariable = (PsiLocalVariable)paramDeclaration.getDeclaredElements()[0];
      parameterDeclarations.put(localVariable, usages);

      for (PsiExpression expression : parameter.myPatternUsages) {
        PsiExpression mapped = expressionsMapping.get(expression);
        if (mapped != null) {
          PsiExpression replacement = factory.createExpressionFromText(parameterName, expression);
          mapped.replace(replacement);
        }
      }
    }

    return parameterDeclarations;
  }

  private static void collectCopyMapping(PsiElement @NotNull [] pattern,
                                         PsiElement @NotNull [] copy,
                                         @NotNull List<? extends ClusterOfUsages> patternUsages,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    Set<PsiExpression> patternExpressions = new HashSet<>();
    for (ClusterOfUsages usages : patternUsages) {
      patternExpressions.addAll(usages.myPatterns);
    }

    collectCopyMapping(pattern, copy, patternExpressions::contains, expressions::put, variables::put);
  }

  public static void collectCopyMapping(PsiElement @NotNull [] pattern,
                                        PsiElement @NotNull [] copy,
                                        @NotNull Predicate<? super PsiExpression> isReplaceablePattern,
                                        @NotNull BiConsumer<? super PsiExpression, ? super PsiExpression> expressionsMapping,
                                        @NotNull BiConsumer<? super PsiVariable, ? super PsiVariable> variablesMapping) {
    pattern = DuplicatesFinder.getDeeplyFilteredElements(pattern);
    copy = DuplicatesFinder.getDeeplyFilteredElements(copy);
    if (copy.length != pattern.length) {
      return; // it's an extracted parameter, so there's no need to go deeper
    }
    for (int i = 0; i < pattern.length; i++) {
      collectCopyMapping(pattern[i], copy[i], isReplaceablePattern, expressionsMapping, variablesMapping);
    }
  }

  private static void collectCopyMapping(@NotNull PsiElement pattern,
                                         @NotNull PsiElement copy,
                                         @NotNull Predicate<? super PsiExpression> isReplaceablePattern,
                                         @NotNull BiConsumer<? super PsiExpression, ? super PsiExpression> expressionsMapping,
                                         @NotNull BiConsumer<? super PsiVariable, ? super PsiVariable> variablesMapping) {
    if (pattern == copy) return;
    if (pattern instanceof PsiExpression && copy instanceof PsiExpression && isReplaceablePattern.test((PsiExpression)pattern)) {
      expressionsMapping.accept((PsiExpression)pattern, (PsiExpression)copy);
      return;
    }

    if (pattern instanceof PsiJavaCodeReferenceElement && copy instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolvedPattern = ((PsiJavaCodeReferenceElement)pattern).resolve();
      PsiElement resolvedCopy = ((PsiJavaCodeReferenceElement)copy).resolve();
      if (resolvedPattern != resolvedCopy && resolvedPattern instanceof PsiVariable && resolvedCopy instanceof PsiVariable) {
        variablesMapping.accept((PsiVariable)resolvedPattern, (PsiVariable)resolvedCopy);
      }
      PsiElement patternQualifier = ((PsiJavaCodeReferenceElement)pattern).getQualifier();
      PsiElement copyQualifier = ((PsiJavaCodeReferenceElement)copy).getQualifier();
      if (patternQualifier != null && copyQualifier != null) {
        collectCopyMapping(patternQualifier, copyQualifier, isReplaceablePattern, expressionsMapping, variablesMapping);
      }
      return;
    }

    if (pattern instanceof PsiVariable && copy instanceof PsiVariable) {
      variablesMapping.accept((PsiVariable)pattern, (PsiVariable)copy);
    }

    collectCopyMapping(pattern.getChildren(), copy.getChildren(), isReplaceablePattern, expressionsMapping, variablesMapping);
  }

  private static PsiElement @NotNull [] getFilteredElements(PsiElement @NotNull [] elements) {
    if (elements.length == 0) {
      return elements;
    }
    ArrayList<PsiElement> result = new ArrayList<>(elements.length);
    for (PsiElement e : elements) {
      if (e == null || e instanceof PsiWhiteSpace || e instanceof PsiComment || e instanceof PsiEmptyStatement) {
        continue;
      }
      if (e instanceof PsiParenthesizedExpression) {
        e = PsiUtil.skipParenthesizedExprDown((PsiParenthesizedExpression)e);
      }
      result.add(e);
    }
    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static class ClusterOfUsages {
    @NotNull private final Set<PsiExpression> myPatterns;
    @NotNull private final Map<Match, ExtractedParameter> myParameters;
    @NotNull private final ExtractedParameter myParameter;
    private final int myFirstOffset;

    ClusterOfUsages(@NotNull ExtractedParameter parameter) {
      myPatterns = parameter.myPatternUsages;
      myParameters = new HashMap<>();
      myParameter = parameter;
      myFirstOffset = myPatterns.stream().mapToInt(PsiElement::getTextOffset).min().orElse(0);
    }

    void putParameter(@NotNull Match match, @NotNull ExtractedParameter parameter) {
      myParameters.put(match, parameter);
    }

    @Nullable
    ExtractedParameter getParameter(@NotNull Match match) {
      return myParameters.get(match);
    }

    boolean arePatternsEquivalent(@NotNull ExtractedParameter parameter) {
      return myPatterns.equals(parameter.myPatternUsages);
    }

    boolean isEquivalent(@NotNull ClusterOfUsages usages, @NotNull Collection<Match> matches) {
      if (!myParameter.myPattern.isEquivalent(usages.myParameter.myPattern)) {
        return false;
      }
      for (Match match : matches) {
        ExtractedParameter parameter = getParameter(match);
        ExtractedParameter otherParameter = usages.getParameter(match);
        if (parameter == null || otherParameter == null || !parameter.myCandidate.isEquivalent(otherParameter.myCandidate)) {
          return false;
        }
      }
      return true;
    }

    static boolean isPatternPresent(@NotNull Map<PsiExpression, ClusterOfUsages> usagesMap, @NotNull ExtractedParameter parameter) {
      return parameter.myPatternUsages.stream().anyMatch(usagesMap::containsKey);
    }

    @Override
    public String toString() {
      return StreamEx.of(myParameters.values()).map(p -> p.myPattern + "->" + p.myCandidate).joining(", ");
    }
  }
}
