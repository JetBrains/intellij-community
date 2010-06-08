/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 08-Jun-2010
 */
package com.intellij.refactoring.extractclass;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractclass.usageInfo.ReplaceStaticVariableAccess;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;

import java.util.*;

public class ExtractEnumProcessor {
  private Project myProject;
  private List<PsiField> myEnumConstants;
  private PsiClass myClass;

  private TypeMigrationProcessor myTypeMigrationProcessor;
  private List<PsiField> myFields;

  public ExtractEnumProcessor(Project project, List<PsiField> enumConstants, List<PsiField> fields, PsiClass aClass) {
    myProject = project;
    myEnumConstants = enumConstants;
    myFields = fields;
    myClass = aClass;
  }


  public void findEnumConstantConflicts(final Ref<UsageInfo[]> refUsages, final MultiMap<PsiElement, String> conflicts) {
    if (hasUsages2Migrate()) {
      final List<UsageInfo> resolvableConflicts = new ArrayList<UsageInfo>();
      for (UsageInfo failedUsage : myTypeMigrationProcessor.getLabeler().getFailedUsages()) {
        final PsiElement element = failedUsage.getElement();
        if (element instanceof PsiReferenceExpression) {
          resolvableConflicts.add(new FixableUsageInfo(element) {
            @Override
            public void fixUsage() throws IncorrectOperationException {
              final PsiReferenceExpression expression = (PsiReferenceExpression)element;
              final String link = PropertyUtil.suggestGetterName("value", expression.getType()) + "()";
              MutationUtils.replaceExpression(expression.getReferenceName() + "." + link, expression);
            }
          });
          continue;
        }
        conflicts.putValue(element, "Failed to migrate");
      }
      if (!resolvableConflicts.isEmpty()) {
        final List<UsageInfo> usageInfos = new ArrayList<UsageInfo>(Arrays.asList(refUsages.get()));
        for (Iterator<UsageInfo> iterator = resolvableConflicts.iterator(); iterator.hasNext();) {
          final UsageInfo conflict = iterator.next();
          for (UsageInfo usageInfo : usageInfos) {
            if (conflict.getElement() == usageInfo.getElement()) {
              iterator.remove();
              break;
            }
          }
        }
        resolvableConflicts.addAll(0, usageInfos);
        refUsages.set(resolvableConflicts.toArray(new UsageInfo[resolvableConflicts.size()]));
      }
    }
    for (final PsiField enumConstant : myEnumConstants) {
      final PsiExpression initializer = enumConstant.getInitializer();
      assert initializer != null;
      initializer.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolved = expression.resolve();
          if (!myEnumConstants.contains(resolved) && myFields.contains(resolved)) {
            conflicts.putValue(initializer, "Enum constant " + RefactoringUIUtil.getDescription(enumConstant, false) +
                                            " would forward reference on field " + RefactoringUIUtil.getDescription(resolved, false));
          }
        }
      });
    }
  }

  private boolean hasUsages2Migrate() {
    return myTypeMigrationProcessor != null;
  }

  public List<FixableUsageInfo> findEnumConstantUsages(List<FixableUsageInfo> fieldUsages) {
    final List<FixableUsageInfo> result = new ArrayList<FixableUsageInfo>();
    if (!myEnumConstants.isEmpty()) {
      final Set<PsiSwitchStatement> switchStatements = new HashSet<PsiSwitchStatement>();
      for (UsageInfo usage : fieldUsages) {
        if (usage instanceof ReplaceStaticVariableAccess) {
          final PsiElement element = usage.getElement();
          final PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement.class);
          if (switchStatement != null) {
            switchStatements.add(switchStatement);
          }
        }
      }

      final PsiConstantEvaluationHelper evaluationHelper =
        JavaPsiFacade.getInstance(myProject).getConstantEvaluationHelper();
      final Set<Object> enumValues = new HashSet<Object>();
      for (PsiField enumConstant : myEnumConstants) {
        enumValues.add(evaluationHelper.computeConstantExpression(enumConstant.getInitializer()));
      }
      final PsiType enumValueType = myEnumConstants.get(0).getType();

      final Set<PsiElement> toMigrate = new HashSet<PsiElement>();
      for (PsiSwitchStatement switchStatement : switchStatements) {
        final PsiStatement errStatement = EnumConstantsUtil.isEnumSwitch(switchStatement, enumValueType, enumValues);
        if (errStatement != null) {
          String description = null;
          if (errStatement instanceof PsiSwitchLabelStatement) {
            final PsiExpression caseValue = ((PsiSwitchLabelStatement)errStatement).getCaseValue();
            if (caseValue != null) {
              description = caseValue.getText() + " can't be replaced with enum";
            }
          }
          result.add(new ConflictUsageInfo(errStatement, description));

        }
        else {
          final PsiExpression expression = switchStatement.getExpression();
          if (expression instanceof PsiReferenceExpression) {
            final PsiElement element = ((PsiReferenceExpression)expression).resolve();
            if (element != null) {
              if (!element.getManager().isInProject(element)) {
                result.add(new ConflictUsageInfo(expression, StringUtil.capitalize(RefactoringUIUtil.getDescription(element, false)) + " is out of project"));
              }
              else {
                toMigrate.add(element);
              }
            }
          }
          else {
            result.add(new ConflictUsageInfo(expression, null));
          }
        }
      }

      if (!toMigrate.isEmpty()) {
        final TypeMigrationRules rules = new TypeMigrationRules(this.myEnumConstants.get(0).getType());
        rules.addConversionDescriptor(new EnumTypeConversionRule());
        rules.setMigrationRootType(
          JavaPsiFacade.getElementFactory(myProject).createType(myClass));
        rules.setBoundScope(GlobalSearchScope.projectScope(myProject));
        myTypeMigrationProcessor = new TypeMigrationProcessor(myProject, toMigrate.toArray(new PsiElement[toMigrate.size()]), rules);
        for (UsageInfo usageInfo : myTypeMigrationProcessor.findUsages()) {
          result.add(new EnumTypeMigrationUsageInfo(usageInfo));
        }
      }
    }
    return result;
  }

  public void performEnumConstantTypeMigration(UsageInfo[] usageInfos) {
    if (hasUsages2Migrate()) {
      final List<UsageInfo> migrationInfos = new ArrayList<UsageInfo>();
      for (UsageInfo usageInfo : usageInfos) {
        if (usageInfo instanceof EnumTypeMigrationUsageInfo) {
          migrationInfos.add(((EnumTypeMigrationUsageInfo)usageInfo).getUsageInfo());
        }
      }
      myTypeMigrationProcessor.performRefactoring(migrationInfos.toArray(new UsageInfo[migrationInfos.size()]));
    }
  }

  private static class EnumTypeMigrationUsageInfo extends FixableUsageInfo {
    private UsageInfo myUsageInfo;

    public EnumTypeMigrationUsageInfo(UsageInfo usageInfo) {
      super(usageInfo.getElement());
      myUsageInfo = usageInfo;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
    }

    public UsageInfo getUsageInfo() {
      return myUsageInfo;
    }
  }

  private static class ConflictUsageInfo extends FixableUsageInfo {
    private final String myDescription;

    public ConflictUsageInfo(PsiElement expression, String description) {
      super(expression);
      myDescription = description;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
    }

    @Override
    public String getConflictMessage() {
      return "Unable to migrate statement to enum constant." + (myDescription != null ? " " + myDescription : "");
    }
  }
}