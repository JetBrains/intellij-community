/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.suspiciousNameCombination;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.AddDeleteListPanel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SuspiciousNameCombinationInspection extends BaseLocalInspectionTool {
  private List<String> myNameGroups = new ArrayList<String>();
  private Map<String, String> myWordToGroupMap = new HashMap<String, String>();
  @NonNls private static final String ELEMENT_GROUPS = "group";
  @NonNls private static final String ATTRIBUTE_NAMES = "names";

  public SuspiciousNameCombinationInspection() {
    addNameGroup("x,width,left,right");
    addNameGroup("y,height,top,bottom");
  }

  private void clearNameGroups() {
    myNameGroups.clear();
    myWordToGroupMap.clear();
  }

  private void addNameGroup(@NonNls final String group) {
    myNameGroups.add(group);
    List<String> words = StringUtil.split(group, ",");
    for(String word: words) {
      myWordToGroupMap.put(word.trim().toLowerCase(), group);
    }
  }

  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("suspicious.name.combination.display.name");
  }

  @NonNls
  public String getShortName() {
    return "SuspiciousNameCombination";
  }

  @Override @Nullable
  public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
    return new MyVisitor(holder);
  }

  @Override @Nullable
  public JComponent createOptionsPanel() {
    return new MyOptionsPanel();
  }

  @Override public void readSettings(Element node) throws InvalidDataException {
    clearNameGroups();
    for(Object o: node.getChildren(ELEMENT_GROUPS)) {
      Element e = (Element) o;
      addNameGroup(e.getAttributeValue(ATTRIBUTE_NAMES));
    }
  }

  @Override public void writeSettings(Element node) throws WriteExternalException {
    for(String group: myNameGroups) {
      Element e = new Element(ELEMENT_GROUPS);
      node.addContent(e);
      e.setAttribute(ATTRIBUTE_NAMES, group);
    }
  }

  private class MyVisitor extends PsiElementVisitor {
    private ProblemsHolder myProblemsHolder;

    public MyVisitor(final ProblemsHolder problemsHolder) {
      myProblemsHolder = problemsHolder;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
    }

    @Override public void visitVariable(PsiVariable variable) {
      if (variable.hasInitializer()) {
        PsiExpression expr = variable.getInitializer();
        if (expr instanceof PsiReferenceExpression) {
          PsiReferenceExpression refExpr = (PsiReferenceExpression) expr;
          checkCombination(variable, variable.getName(), refExpr.getReferenceName(), "suspicious.name.assignment");
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
      String[] words = PsiNameHelper.splitNameIntoWords(name);
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

  private class MyOptionsPanel extends AddDeleteListPanel {
    private JButton myEditButton;

    public MyOptionsPanel() {
      super(InspectionsBundle.message("suspicious.name.combination.options.title"), myNameGroups);
      myEditButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editSelectedItem();
        }
      });
      myList.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            editSelectedItem();
          }
        }
      });
      myListModel.addListDataListener(new ListDataListener() {
        public void intervalAdded(ListDataEvent e) {
          saveChanges();
        }

        public void intervalRemoved(ListDataEvent e) {
          saveChanges();
        }

        public void contentsChanged(ListDataEvent e) {
          saveChanges();
        }
      });
    }

    @Override protected JButton[] createButtons() {
      myEditButton = new JButton(CommonBundle.message("button.edit"));
      return new JButton[] { myAddButton, myEditButton, myDeleteButton };
    }

    protected Object findItemToAdd() {
      return Messages.showInputDialog(this,
                                      InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                      InspectionsBundle.message("suspicious.name.combination.add.titile"),
                                      Messages.getQuestionIcon(), "", null);
    }

    private void editSelectedItem() {
      int index = myList.getSelectedIndex();
      if (index >= 0) {
        String inputValue = (String) myListModel.get(index);
        String newValue = Messages.showInputDialog(this,
                                                   InspectionsBundle.message("suspicious.name.combination.options.prompt"),
                                                   InspectionsBundle.message("suspicious.name.combination.edit.title"),
                                                   Messages.getQuestionIcon(),
                                                   inputValue, null);
        if (newValue != null) {
          myListModel.set(index, newValue);
        }
      }
    }

    private void saveChanges() {
      clearNameGroups();
      for(int i=0; i<myListModel.getSize(); i++) {
        addNameGroup((String) myListModel.getElementAt(i));
      }
    }
  }
}
