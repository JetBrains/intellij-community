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
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.ExtractedParameter;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.VariableReturnValue;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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
  private List<Occurrences> myOccurrencesList;
  private PsiMethod myParametrizedMethod;
  private PsiMethodCallExpression myParametrizedCall;
  private VariableData[] myVariableData;

  public ParametrizedDuplicates(PsiElement[] pattern) {
    if (pattern[0] instanceof PsiStatement) {
      Project project = pattern[0].getProject();
      PsiElement[] copy = IntroduceParameterHandler.getElementsInCopy(project, pattern[0].getContainingFile(), pattern);
      myElements = wrapWithCodeBlock(copy);
    }
    else {
      myElements = PsiElement.EMPTY_ARRAY;
    }
  }

  @Nullable
  public static ParametrizedDuplicates findDuplicates(@NotNull ExtractMethodProcessor originalProcessor) {
    PsiElement[] pattern = originalProcessor.myElements;
    if (pattern.length == 0) {
      return null;
    }
    List<Match> matches = findOriginalDuplicates(originalProcessor);
    if (matches.isEmpty()) {
      return null;
    }

    ParametrizedDuplicates duplicates = new ParametrizedDuplicates(pattern);
    if (!duplicates.initMatches(matches)) {
      return null;
    }

    if (!duplicates.extract(originalProcessor)) {
      return null;
    }
    return duplicates;
  }

  @NotNull
  private static List<Match> findOriginalDuplicates(@NotNull ExtractMethodProcessor processor) {
    PsiElement[] elements = getFilteredElements(processor.myElements);

    DuplicatesFinder finder = new DuplicatesFinder(elements, processor.myInputVariables.copy(),
                                                   processor.myOutputVariable != null
                                                   ? new VariableReturnValue(processor.myOutputVariable) : null,
                                                   Arrays.asList(processor.myOutputVariables), true) {
      @Override
      protected boolean isSelf(@NotNull PsiElement candidate) {
        for (PsiElement element : elements) {
          if (PsiTreeUtil.isAncestor(element, candidate, false)) {
            return true;
          }
        }
        return false;
      }
    };
    return finder.findDuplicates(processor.myTargetClass);
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

  private boolean initMatches(@NotNull List<Match> matches) {
    myOccurrencesList = new ArrayList<>();
    Map<PsiExpression, Occurrences> occurrencesMap = new THashMap<>();
    Set<Match> badMatches = new THashSet<>();
    for (Match match : matches) {
      List<Occurrences> occurrencesInMatch = getOccurrencesInMatch(occurrencesMap, match);
      if (occurrencesInMatch == null) {
        badMatches.add(match);
        continue;
      }
      for (Occurrences occurrences : occurrencesInMatch) {
        myOccurrencesList.add(occurrences);
        for (PsiExpression expression : occurrences.myPatterns) {
          occurrencesMap.put(expression, occurrences);
        }
      }
    }

    if (!badMatches.isEmpty()) {
      matches = new ArrayList<>(matches);
      matches.removeAll(badMatches);
    }
    myMatches = matches;
    return !myMatches.isEmpty() && !myOccurrencesList.isEmpty();
  }

  @Nullable
  private static List<Occurrences> getOccurrencesInMatch(@NotNull Map<PsiExpression, Occurrences> occurrencesMap, @NotNull Match match) {
    List<Occurrences> matchOccurrences = new ArrayList<>();
    List<ExtractedParameter> parameters = match.getExtractedParameters();
    for (ExtractedParameter parameter : parameters) {
      Occurrences occurrences = occurrencesMap.get(parameter.myPattern.getUsage());
      if (occurrences != null && !occurrences.isEquivalent(parameter) ||
          occurrences == null && Occurrences.isPresent(occurrencesMap, parameter)) {
        return null;
      }
      if (occurrences == null) {
        matchOccurrences.add(occurrences = new Occurrences(parameter));
      }
      occurrences.add(parameter);
    }
    return matchOccurrences;
  }

  private boolean extract(@NotNull ExtractMethodProcessor originalProcessor) {
    Map<PsiExpression, PsiExpression> expressionsMapping = new THashMap<>();
    Map<PsiVariable, PsiVariable> variablesMapping = new THashMap<>();
    collectCopyMapping(originalProcessor.myElements, myElements, myOccurrencesList, expressionsMapping, variablesMapping);

    Map<PsiLocalVariable, Occurrences> parameterDeclarations = createParameterDeclarations(originalProcessor, expressionsMapping);
    putMatchParameters(parameterDeclarations);

    JavaDuplicatesExtractMethodProcessor parametrizedProcessor = new JavaDuplicatesExtractMethodProcessor(myElements, REFACTORING_NAME) {
      @Override
      protected boolean isFoldingApplicable() {
        return false;
      }
    };
    if (!parametrizedProcessor.prepare(false)) {
      return false;
    }
    parametrizedProcessor.applyFrom(originalProcessor, variablesMapping);
    parametrizedProcessor.doExtract();
    parametrizedProcessor.setDataFromInputVariables();
    myParametrizedMethod = parametrizedProcessor.getExtractedMethod();
    myParametrizedCall = parametrizedProcessor.getMethodCall();
    myVariableData = parametrizedProcessor.myVariableDatum;
    replaceArguments(parameterDeclarations, myParametrizedCall);

    return true;
  }

  private static void replaceArguments(Map<PsiLocalVariable, Occurrences> parameterDeclarations, PsiMethodCallExpression parametrizedCall) {
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

  private void putMatchParameters(Map<PsiLocalVariable, Occurrences> parameterDeclarations) {
    Map<PsiExpression, PsiLocalVariable> patternUsageToParameter = new THashMap<>();
    for (Map.Entry<PsiLocalVariable, Occurrences> entry : parameterDeclarations.entrySet()) {
      PsiExpression usage = entry.getValue().myParameters.get(0).myPattern.getUsage();
      patternUsageToParameter.put(usage, entry.getKey());
    }

    for (Match match : myMatches) {
      List<ExtractedParameter> matchedParameters = match.getExtractedParameters();
      for (ExtractedParameter matchedParameter : matchedParameters) {
        PsiLocalVariable localVariable = patternUsageToParameter.get(matchedParameter.myPattern.getUsage());
        LOG.assertTrue(localVariable != null, "match local variable");
        boolean ok = match.putParameter(Pair.createNonNull(localVariable, matchedParameter.myType),
                                        matchedParameter.myCandidate.getUsage());
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

  public VariableData[] getVariableData() {
    return myVariableData;
  }

  public int getSize() {
    return myMatches != null ? myMatches.size() : 0;
  }

  public List<Match> getDuplicates() {
    return myMatches;
  }

  @NotNull
  private static PsiElement[] wrapWithCodeBlock(@NotNull PsiElement[] elements) {
    PsiElement parent = elements[0].getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(elements[0].getProject());
    PsiBlockStatement statement = (PsiBlockStatement)factory.createStatementFromText("{}", parent);
    statement.getCodeBlock().addRange(elements[0], elements[elements.length - 1]);
    statement = (PsiBlockStatement)parent.addBefore(statement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);
    PsiCodeBlock codeBlock = statement.getCodeBlock();
    PsiElement[] elementsInCopy = codeBlock.getChildren();
    LOG.assertTrue(elementsInCopy.length >= elements.length + 2, "wrapper block length is too small");
    return Arrays.copyOfRange(elementsInCopy, 1, elementsInCopy.length - 1);
  }

  @NotNull
  private Map<PsiLocalVariable, Occurrences> createParameterDeclarations(@NotNull ExtractMethodProcessor originalProcessor,
                                                                    @NotNull Map<PsiExpression, PsiExpression> expressionsMapping) {

    Project project = myElements[0].getProject();
    Map<PsiLocalVariable, Occurrences> parameterDeclarations = new THashMap<>();
    UniqueNameGenerator generator = originalProcessor.getParameterNameGenerator(myElements[0]);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiElement parent = myElements[0].getParent();
    for (Occurrences occurrences : myOccurrencesList) {
      ExtractedParameter parameter = occurrences.myParameters.get(0);
      PsiExpression patternUsage = parameter.myPattern.getUsage();
      String usageText = patternUsage.getText();
      PsiExpression exprInCopy = factory.createExpressionFromText(usageText, parent);
      final SuggestedNameInfo info =
        JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, exprInCopy, null);
      final String parameterName = generator.generateUniqueName(info.names.length > 0 ? info.names[0] : "p");

      String declarationText = parameter.myType.getCanonicalText() + " " + parameterName + " = " + usageText + ";";
      PsiDeclarationStatement paramDeclaration = (PsiDeclarationStatement)factory.createStatementFromText(declarationText, parent);
      paramDeclaration = (PsiDeclarationStatement)parent.addBefore(paramDeclaration, myElements[0]);
      PsiLocalVariable localVariable = (PsiLocalVariable)paramDeclaration.getDeclaredElements()[0];
      parameterDeclarations.put(localVariable, occurrences);

      for (PsiExpression expression : parameter.myUsages.keySet()) {
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
                                         @NotNull List<Occurrences> patternUsages,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    Set<PsiExpression> patternExpressions = new THashSet<>();
    for (Occurrences occurrences : patternUsages) {
      patternExpressions.addAll(occurrences.myPatterns);
    }

    collectCopyMapping(pattern, copy, patternExpressions, expressions, variables);
  }

  private static void collectCopyMapping(@NotNull PsiElement[] pattern,
                                         @NotNull PsiElement[] copy,
                                         @NotNull Set<PsiExpression> replaceablePatterns,
                                         @NotNull Map<PsiExpression, PsiExpression> expressions,
                                         @NotNull Map<PsiVariable, PsiVariable> variables) {
    pattern = getFilteredElements(pattern);
    copy = getFilteredElements(copy);
    LOG.assertTrue(copy.length == pattern.length, "copy length");
    for (int i = 0; i < pattern.length; i++) {
      collectCopyMapping(pattern[i], copy[i], replaceablePatterns, expressions, variables);
    }
  }

  private static void collectCopyMapping(PsiElement pattern,
                                         PsiElement copy,
                                         Set<PsiExpression> replaceablePatterns,
                                         Map<PsiExpression, PsiExpression> expressions,
                                         Map<PsiVariable, PsiVariable> variables) {
    if (pattern == copy) return;
    LOG.assertTrue(pattern != null && copy != null, "null in collectVariablesMapping");
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

  private static class Occurrences {
    @NotNull private final Set<PsiExpression> myPatterns;
    @NotNull private final List<ExtractedParameter> myParameters;

    public Occurrences(ExtractedParameter parameter) {
      myPatterns = parameter.myUsages.keySet();
      myParameters = new ArrayList<>();
    }

    public void add(ExtractedParameter parameter) {
      myParameters.add(parameter);
    }

    public boolean isEquivalent(ExtractedParameter parameter) {
      return myPatterns.equals(parameter.myUsages.keySet());
    }

    public static boolean isPresent(Map<PsiExpression, Occurrences> usagesMap, @NotNull ExtractedParameter parameter) {
      return parameter.myUsages.keySet().stream().anyMatch(expression -> usagesMap.get(expression) != null);
    }
  }
}
