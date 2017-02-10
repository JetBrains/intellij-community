/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.suspiciousNameCombination;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuspiciousNameCombinationInspectionBase extends BaseJavaBatchLocalInspectionTool {
  @NonNls private static final String ELEMENT_GROUPS = "group";
  @NonNls private static final String ATTRIBUTE_NAMES = "names";
  protected final List<String> myNameGroups = new ArrayList<>();
  private final Map<String, String> myWordToGroupMap = new HashMap<>();

  public SuspiciousNameCombinationInspectionBase() {
    addNameGroup("x,width,left,right");
    addNameGroup("y,height,top,bottom");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  protected void clearNameGroups() {
    myNameGroups.clear();
    myWordToGroupMap.clear();
  }

  protected void addNameGroup(@NonNls final String group) {
    myNameGroups.add(group);
    List<String> words = StringUtil.split(group, ",");
    for(String word: words) {
      myWordToGroupMap.put(word.trim().toLowerCase(), group);
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("suspicious.name.combination.display.name");
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
    for(Object o: node.getChildren(ELEMENT_GROUPS)) {
      Element e = (Element) o;
      addNameGroup(e.getAttributeValue(ATTRIBUTE_NAMES));
    }
  }

  @Override public void writeSettings(@NotNull Element node) throws WriteExternalException {
    for(String group: myNameGroups) {
      Element e = new Element(ELEMENT_GROUPS);
      node.addContent(e);
      e.setAttribute(ATTRIBUTE_NAMES, group);
    }
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myProblemsHolder;

    public MyVisitor(final ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }
    @Override public void visitVariable(PsiVariable variable) {
      if (variable.hasInitializer()) {
        PsiExpression expr = variable.getInitializer();
        if (expr instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression) expr;
          PsiIdentifier nameIdentifier = variable.getNameIdentifier();
          checkCombination(nameIdentifier != null ? nameIdentifier : variable, variable.getName(), refExpr.getReferenceName(), "suspicious.name.assignment");
        }
      }
    }

    @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      PsiExpression lhs = expression.getLExpression();
      PsiExpression rhs = expression.getRExpression();
      if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
        PsiReferenceExpression lhsExpr = (PsiReferenceExpression) lhs;
        PsiReferenceExpression rhsExpr = (PsiReferenceExpression) rhs;
        checkCombination(lhsExpr, lhsExpr.getReferenceName(), rhsExpr.getReferenceName(), "suspicious.name.assignment");
      }
    }

    @Override public void visitCallExpression(PsiCallExpression expression) {
      final PsiMethod psiMethod = expression.resolveMethod();
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
    public void visitReturnStatement(final PsiReturnStatement statement) {
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
                                  final String key) {
      String nameGroup1 = findNameGroup(name);
      String nameGroup2 = findNameGroup(referenceName);
      if (nameGroup1 != null && nameGroup2 != null && !nameGroup1.equals(nameGroup2)) {
        myProblemsHolder.registerProblem(location, JavaErrorMessages.message(key, referenceName, name));
      }
    }

    @Nullable private String findNameGroup(@Nullable final String name) {
      if (name == null) {
        return null;
      }
      String[] words = NameUtil.splitNameIntoWords(name);
      String result = null;
      for(String word: words) {
        String group = myWordToGroupMap.get(word.toLowerCase());
        if (group != null) {
          if (result == null) {
            result = group;
          }
          else if (!result.equals(group)) {
            result = null;
            break;
          }
        }
      }
      return result;
    }
  }
}
