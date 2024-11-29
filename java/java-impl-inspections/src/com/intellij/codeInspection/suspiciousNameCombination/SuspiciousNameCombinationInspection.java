// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.suspiciousNameCombination;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;


public final class SuspiciousNameCombinationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls private static final String ELEMENT_GROUPS = "group";
  @NonNls private static final String ATTRIBUTE_NAMES = "names";
  @NonNls private static final String ELEMENT_IGNORED_METHODS = "ignored";
  private final List<String> myNameGroups = new ArrayList<>();
  final MethodMatcher myIgnoredMethods = new MethodMatcher()
    // parameter name is 'x' which is completely unrelated to coordinates
    .add("java.io.PrintStream", "println")
    .add("java.io.PrintWriter", "println")
    .add("java.lang.System", "identityHashCode")
    .add("java.sql.PreparedStatement", "set.*")
    .add("java.sql.ResultSet", "update.*")
    .add("java.sql.SQLOutput", "write.*")
    // parameters for compare methods are x and y which is also unrelated to coordinates
    .add("java.lang.Integer", "compare.*|toUnsignedLong")
    .add("java.lang.Long", "compare.*")
    .add("java.lang.Short", "compare|toUnsigned.*")
    .add("java.lang.Byte", "compare|toUnsigned.*")
    .add("java.lang.Character", "compare")
    .add("java.lang.Boolean", "compare")
    // parameter names for addExact, multiplyFull, floorDiv, hypot etc. are x and y,
    // but either unlikely to be related to coordinates or their order does not matter (like in hypot)
    .add("java.lang.Math", ".*")
    .add("java.lang.StrictMath", ".*");
  private final Map<String, String> myWordToGroupMap = new HashMap<>();
  private int myLongestWord = 0;

  public SuspiciousNameCombinationInspection() {
    addNameGroup("x,width,left,right");
    addNameGroup("y,height,top,bottom");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("myNameGroups", AnalysisBundle.message("suspicious.name.combination.options.title")),
      myIgnoredMethods.getTable(JavaBundle.message("section.title.inspection.suspicious.names.ignore.methods")).prefix("myIgnoredMethods")
    );
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private void clearNameGroups() {
    myNameGroups.clear();
    myWordToGroupMap.clear();
    myLongestWord = 0;
  }

  public void addNameGroup(@NonNls final String group) {
    myNameGroups.add(group);
    List<String> words = StringUtil.split(group, ",");
    for(String word: words) {
      String canonicalized = canonicalize(word);
      myLongestWord = Math.max(myLongestWord, canonicalized.length());
      myWordToGroupMap.put(canonicalized, group);
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "SuspiciousNameCombination";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  @Override public void readSettings(@NotNull Element node) throws InvalidDataException {
    clearNameGroups();
    for(Element e: node.getChildren(ELEMENT_GROUPS)) {
      addNameGroup(e.getAttributeValue(ATTRIBUTE_NAMES));
    }
    Element ignoredMethods = node.getChild(ELEMENT_IGNORED_METHODS);
    if (ignoredMethods != null) {
      myIgnoredMethods.readSettings(ignoredMethods);
    }
  }

  @Override public void writeSettings(@NotNull Element node) throws WriteExternalException {
    for(String group: myNameGroups) {
      Element e = new Element(ELEMENT_GROUPS);
      node.addContent(e);
      e.setAttribute(ATTRIBUTE_NAMES, group);
    }
    Element ignoredMethods = new Element(ELEMENT_IGNORED_METHODS);
    node.addContent(ignoredMethods);
    myIgnoredMethods.writeSettings(ignoredMethods);
  }

  @NotNull
  private static String canonicalize(String word) {
    return StringUtil.toLowerCase(word.trim());
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myProblemsHolder;

    MyVisitor(final ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }
    @Override public void visitVariable(@NotNull PsiVariable variable) {
      if (variable.hasInitializer()) {
        PsiExpression expr = variable.getInitializer();
        if (expr instanceof PsiReferenceExpression refExpr) {
          PsiIdentifier nameIdentifier = variable.getNameIdentifier();
          checkCombination(nameIdentifier != null ? nameIdentifier : variable, variable.getName(), refExpr.getReferenceName(), "suspicious.name.assignment");
        }
      }
    }

    @Override public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      PsiExpression lhs = expression.getLExpression();
      PsiExpression rhs = expression.getRExpression();
      if (lhs instanceof PsiReferenceExpression lhsExpr && rhs instanceof PsiReferenceExpression rhsExpr) {
        checkCombination(lhsExpr, lhsExpr.getReferenceName(), rhsExpr.getReferenceName(), "suspicious.name.assignment");
      }
    }

    @Override public void visitCallExpression(@NotNull PsiCallExpression expression) {
      final PsiMethod psiMethod = expression.resolveMethod();
      if (myIgnoredMethods.matches(psiMethod)) return;
      final PsiExpressionList argList = expression.getArgumentList();
      if (psiMethod != null && argList != null) {
        final PsiExpression[] args = argList.getExpressions();
        final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        for(int i=0; i<parameters.length; i++) {
          if (i >= args.length) break;
          if (args [i] instanceof PsiReferenceExpression) {
            // PsiParameter.getName() can be expensive for compiled class files, so check reference name before
            // fetching parameter name
            final String refName = ((PsiReferenceExpression)args[i]).getReferenceName();
            if (findNameGroup(refName) != null) {
              checkCombination(args [i], parameters [i].getName(), refName, "suspicious.name.parameter");
            }
          }
        }
      }
    }

    @Override
    public void visitReturnStatement(final @NotNull PsiReturnStatement statement) {
      final PsiExpression returnValue = statement.getReturnValue();
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(returnValue, PsiMethod.class, true, PsiLambdaExpression.class);
      if (returnValue instanceof PsiReferenceExpression && containingMethod != null) {
        final String refName = ((PsiReferenceExpression)returnValue).getReferenceName();
        checkCombination(returnValue, containingMethod.getName(), refName, "suspicious.name.return");
      }
    }

    private void checkCombination(final PsiElement location,
                                  @Nullable final String name,
                                  @Nullable final String referenceName,
                                  @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key) {
      String nameGroup1 = findNameGroup(name);
      String nameGroup2 = findNameGroup(referenceName);
      if (nameGroup1 != null && nameGroup2 != null && !nameGroup1.equals(nameGroup2)) {
        myProblemsHolder.registerProblem(location, JavaErrorBundle.message(key, referenceName, name));
      }
    }

    @Nullable private String findNameGroup(@Nullable final String name) {
      if (name == null) {
        return null;
      }
      String[] words = NameUtil.splitNameIntoWords(name);
      Arrays.asList(words).replaceAll(SuspiciousNameCombinationInspection::canonicalize);
      String result = null;
      for (int i = 0; i < words.length; i++) {
        String word = "";
        for (int j = i; j < words.length; j++) {
          word += words[j];
          if (word.length() > myLongestWord) break;
          String group = myWordToGroupMap.get(word);
          if (group != null) {
            if (result == null) {
              result = group;
            }
            else if (!result.equals(group)) {
              return null;
            }
          }
        }
      }
      return result;
    }
  }
}
