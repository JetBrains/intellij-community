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
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.AddEditDeleteListPanel;
import com.intellij.ui.IdeBorderFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author yole
 */
public class SuspiciousNameCombinationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls private static final String ELEMENT_GROUPS = "group";
  @NonNls private static final String ATTRIBUTE_NAMES = "names";
  @NonNls private static final String ELEMENT_IGNORED_METHODS = "ignored";
  protected final List<String> myNameGroups = new ArrayList<>();
  final MethodMatcher myIgnoredMethods = new MethodMatcher()
    // parameter name is 'x' which is completely unrelated to coordinates
    .add("java.io.PrintStream", "println")
    .add("java.io.PrintWriter", "println")
    .add("java.lang.System", "identityHashCode")
    .add("java.sql.PreparedStatement", "set.*")
    .add("java.sql.ResultSet", "update.*")
    .add("java.sql.SQLOutput", "write.*")
    // parameters for compare methods are x and y which is also unrelated to coordinates
    .add("java.lang.Integer", "compare.*")
    .add("java.lang.Long", "compare.*")
    .add("java.lang.Short", "compare")
    .add("java.lang.Byte", "compare")
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

  @Override @Nullable
  public JComponent createOptionsPanel() {
    NameGroupsPanel nameGroupsPanel = new NameGroupsPanel();
    ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myIgnoredMethods.getClassNames(), myIgnoredMethods.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.class"));
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(nameGroupsPanel);
    tablePanel.setBorder(IdeBorderFactory.createTitledBorder("Ignore methods", false));
    panel.add(tablePanel);
    return panel;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  protected void clearNameGroups() {
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

  private class NameGroupsPanel extends AddEditDeleteListPanel<String> {

    NameGroupsPanel() {
      super(InspectionsBundle.message("suspicious.name.combination.options.title"), myNameGroups);
      myListModel.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
          saveChanges();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
          saveChanges();
        }
      });
    }

    @Override
    protected String findItemToAdd() {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.add.title"),
                                      Messages.getQuestionIcon(), "", null);
    }

    @Override
    protected String editSelectedItem(String inputValue) {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.edit.title"),
                                      Messages.getQuestionIcon(),
                                      inputValue, null);
    }

    private void saveChanges() {
      clearNameGroups();
      for(int i=0; i<myListModel.getSize(); i++) {
        addNameGroup(myListModel.getElementAt(i));
      }
    }
  }

  private class MyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myProblemsHolder;

    MyVisitor(final ProblemsHolder problemsHolder) {
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
