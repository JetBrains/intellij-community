/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.refactoring.extractMethod.ExtractMethodHandler.REFACTORING_NAME;

/**
 * @author Pavel.Dolgov
 */
public class ParametrizedDuplicates {
  private static final Logger LOG = Logger.getInstance(ParametrizedDuplicates.class);

  private final PsiElement[] myElements;
  private List<Match> myMatches;
  private List<ClusterOfUsages> myUsagesList;
  private PsiMethod myParametrizedMethod;
  private PsiMethodCallExpression myParametrizedCall;
  private VariableData[] myVariableDatum;

  private ParametrizedDuplicates(@NotNull PsiElement[] pattern,
                                 @NotNull ExtractMethodProcessor originalProcessor) {
    pattern = getFilteredElements(pattern);
    PsiElement firstElement = pattern.length != 0 ? pattern[0] : null;
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

  private static PsiElement[] copyElements(@NotNull PsiElement[] pattern) {
    Project project = pattern[0].getProject();
    return IntroduceParameterHandler.getElementsInCopy(project, pattern[0].getContainingFile(), pattern, false);
  }

  @Nullable
  public static ParametrizedDuplicates findDuplicates(@NotNull ExtractMethodProcessor originalProcessor) {
    PsiElement[] pattern = originalProcessor.myElements;
    if (pattern.length == 0) {
      return null;
    }

    DuplicatesFinder finder = createDuplicatesFinder(originalProcessor, DuplicatesFinder.MatchType.PARAMETRIZED);
    List<Match> matches = finder.findDuplicates(originalProcessor.myTargetClass);
    matches = filterNestedSubexpressions(matches);
    if (matches.isEmpty()) {
      return null;
    }

    Map<PsiExpression, String> predefinedNames = foldParameters(originalProcessor, matches);

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
    DuplicatesFinder finder = createDuplicatesFinder(originalProcessor, DuplicatesFinder.MatchType.FOLDED);
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

  @NotNull
  private static DuplicatesFinder createDuplicatesFinder(@NotNull ExtractMethodProcessor processor,
                                                         @NotNull DuplicatesFinder.MatchType matchType) {
    PsiElement[] elements = getFilteredElements(processor.myElements);
    Set<PsiVariable> effectivelyLocal = processor.getEffectivelyLocalVariables();

    InputVariables inputVariables = matchType == DuplicatesFinder.MatchType.PARAMETRIZED
                                    ? processor.myInputVariables.copyWithoutFolding() : processor.myInputVariables;
    ReturnValue returnValue = processor.myOutputVariable != null ? new VariableReturnValue(processor.myOutputVariable) : null;
    return new DuplicatesFinder(elements, inputVariables, returnValue,
                                Collections.emptyList(), matchType, effectivelyLocal);
  }

  @NotNull
  public PsiMethod replaceMethod(@NotNull PsiMethod originalMethod) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalMethod.getProject());
    String text = myParametrizedMethod.getText();
    PsiMethod method = factory.createMethodFromText(text, originalMethod.getParent());
    return (PsiMethod)originalMethod.replace(method);
  }

  @NotNull
  public PsiMethodCallExpression replaceCall(@NotNull PsiMethodCallExpression originalCall) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalCall.getProject());
    String text = myParametrizedCall.getText();
    PsiMethodCallExpression call = (PsiMethodCallExpression)factory.createExpressionFromText(text, originalCall.getParent());
    return (PsiMethodCallExpression)originalCall.replace(call);
  }

  private boolean initMatches(@NotNull PsiElement[] pattern, @NotNull List<Match> matches) {
    if (myElements.length == 0) {
      return false;
    }

    myUsagesList = new ArrayList<>();
    Map<PsiExpression, ClusterOfUsages> usagesMap = new THashMap<>();
    Set<Match> badMatches = new THashSet<>();
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
        ExtractedParameter parameter = usages.myParameters.get(match);
        if (parameter == null) {
          Map<PsiExpression, PsiExpression> expressions =
            expressionsMapping.computeIfAbsent(match, unused -> {
              Map<PsiExpression, PsiExpression> result = new HashMap<>();
              collectCopyMapping(pattern, match.getMatchElements(), usagesMap.keySet(), result, new HashMap<>());
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

    myUsagesList.sort(Comparator.comparing(usages -> usages.myFirstOffset));
    return true;
  }

  private static List<Match> filterNestedSubexpressions(List<Match> matches) {
    Map<PsiExpression, Set<Match>> patternUsages = new THashMap<>();
    for (Match match : matches) {
      for (ExtractedParameter parameter : match.getExtractedParameters()) {
        for (PsiExpression patternUsage : parameter.myPatternUsages) {
          patternUsages.computeIfAbsent(patternUsage, k -> new THashSet<>()).add(match);
        }
      }
    }

    Set<Match> badMatches = new THashSet<>();
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
      if (usages != null && !usages.isEquivalent(parameter) ||
          usages == null && ClusterOfUsages.isPresent(usagesMap, parameter)) {
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
    Map<PsiExpression, PsiExpression> expressionsMapping = new THashMap<>();
    Map<PsiVariable, PsiVariable> variablesMapping = new THashMap<>();
    collectCopyMapping(originalProcessor.myElements, myElements, myUsagesList, expressionsMapping, variablesMapping);

    Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations =
      createParameterDeclarations(originalProcessor, expressionsMapping, predefinedNames);
    putMatchParameters(parameterDeclarations);

    JavaDuplicatesExtractMethodProcessor parametrizedProcessor = new JavaDuplicatesExtractMethodProcessor(myElements, REFACTORING_NAME);
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

  @NotNull
  private static VariableData[] unmapVariableData(@NotNull VariableData[] variableDatum,
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
    Map<PsiExpression, PsiLocalVariable> patternUsageToParameter = new THashMap<>();
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

  public VariableData[] getVariableDatum() {
    return myVariableDatum;
  }

  public int getSize() {
    return myMatches != null ? myMatches.size() : 0;
  }

  public List<Match> getDuplicates() {
    return myMatches;
  }

  @NotNull
  private static PsiElement[] wrapWithCodeBlock(@NotNull PsiElement[] elements, @NotNull InputVariables inputVariables) {
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

    PsiCodeBlock codeBlock = statement.getCodeBlock();
    PsiElement[] elementsInBlock = codeBlock.getChildren();
    LOG.assertTrue(elementsInBlock.length >= elements.length + 2, "wrapper block length is too small");
    elementsInBlock = Arrays.copyOfRange(elementsInBlock, 1, elementsInBlock.length - 1);

    declareReusedLocalVariables(reusedLocalVariables, statement, factory);
    return elementsInBlock;
  }

  private static void declareReusedLocalVariables(@NotNull List<ReusedLocalVariable> reusedLocalVariables,
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
  private static PsiExpression wrapExpressionWithCodeBlock(@NotNull PsiElement[] copy,
                                                           @NotNull ExtractMethodProcessor originalProcessor) {
    if (copy.length != 1 || !(copy[0] instanceof PsiExpression)) return null;

    PsiExpression expression = (PsiExpression)copy[0];
    PsiType type = expression.getType();
    if (type == null || PsiType.NULL.equals(type)) return null;

    PsiElement parent = expression.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (parentClass == null) return null;

    PsiElement parentClassStart = parentClass.getLBrace();
    if (parentClassStart == null) return null;

    // It's syntactically correct to write "new Object() {void foo(){}}.foo()" - see JLS 15.9.5
    String wrapperBodyText = (PsiType.VOID.equals(type) ? "" : "return ") + expression.getText() + ";";
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
    if (PsiType.VOID.equals(type) && bodyStatement instanceof PsiExpressionStatement) {
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
    Map<PsiLocalVariable, ClusterOfUsages> parameterDeclarations = new THashMap<>();
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

  private static void collectCopyMapping(@NotNull PsiElement[] pattern,
                                         @NotNull PsiElement[] copy,
                                         @NotNull List<ClusterOfUsages> patternUsages,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    Set<PsiExpression> patternExpressions = new THashSet<>();
    for (ClusterOfUsages usages : patternUsages) {
      patternExpressions.addAll(usages.myPatterns);
    }

    collectCopyMapping(pattern, copy, patternExpressions, expressions, variables);
  }

  private static void collectCopyMapping(@NotNull PsiElement[] pattern,
                                         @NotNull PsiElement[] copy,
                                         @NotNull Set<PsiExpression> replaceablePatterns,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    pattern = DuplicatesFinder.getDeeplyFilteredElements(pattern);
    copy = DuplicatesFinder.getDeeplyFilteredElements(copy);
    if (copy.length != pattern.length) {
      return; // it's an extracted parameter, so there's no need to go deeper
    }
    for (int i = 0; i < pattern.length; i++) {
      collectCopyMapping(pattern[i], copy[i], replaceablePatterns, expressions, variables);
    }
  }

  private static void collectCopyMapping(@NotNull PsiElement pattern,
                                         @NotNull PsiElement copy,
                                         @NotNull Set<PsiExpression> replaceablePatterns,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    if (pattern == copy) return;
    if (pattern instanceof PsiExpression && copy instanceof PsiExpression && replaceablePatterns.contains(pattern)) {
      expressions.put((PsiExpression)pattern, (PsiExpression)copy);
    }

    if (pattern instanceof PsiReferenceExpression && copy instanceof PsiReferenceExpression) {
      PsiElement resolvedPattern = ((PsiReferenceExpression)pattern).resolve();
      PsiElement resolvedCopy = ((PsiReferenceExpression)copy).resolve();
      if (resolvedPattern != resolvedCopy && resolvedPattern instanceof PsiVariable && resolvedCopy instanceof PsiVariable) {
        variables.put((PsiVariable)resolvedPattern, (PsiVariable)resolvedCopy);
      }
    }
    else if (pattern instanceof PsiVariable && copy instanceof PsiVariable) {
      variables.put((PsiVariable)pattern, (PsiVariable)copy);
    }

    collectCopyMapping(pattern.getChildren(), copy.getChildren(), replaceablePatterns, expressions, variables);
  }

  @NotNull
  private static PsiElement[] getFilteredElements(@NotNull PsiElement[] elements) {
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

    public ClusterOfUsages(@NotNull ExtractedParameter parameter) {
      myPatterns = parameter.myPatternUsages;
      myParameters = new THashMap<>();
      myParameter = parameter;
      myFirstOffset = myPatterns.stream().mapToInt(PsiElement::getTextOffset).min().orElse(0);
    }

    public void putParameter(Match match, ExtractedParameter parameter) {
      myParameters.put(match, parameter);
    }

    public boolean isEquivalent(ExtractedParameter parameter) {
      return myPatterns.equals(parameter.myPatternUsages);
    }

    public static boolean isPresent(Map<PsiExpression, ClusterOfUsages> usagesMap, @NotNull ExtractedParameter parameter) {
      return parameter.myPatternUsages.stream().anyMatch(usagesMap::containsKey);
    }
  }
}
