/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CreateFieldFromParameterAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.CreateFieldFromParameterAction");
  private String myName = "";

  @Nullable
  private static PsiType[] getTypes(final PsiParameter parameter) {
    if (parameter == null) return null;
    PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    final PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = result.getElement();
    if (psiClass == null) return new PsiType[] {type};
    final HashSet<PsiTypeParameter> usedTypeParameters = new HashSet<PsiTypeParameter>();
    RefactoringUtil.collectTypeParameters(usedTypeParameters, parameter);
    for (Iterator<PsiTypeParameter> iterator = usedTypeParameters.iterator(); iterator.hasNext();) {
      PsiTypeParameter usedTypeParameter = iterator.next();
      if (parameter.getDeclarationScope() != usedTypeParameter.getOwner()) {
        iterator.remove();
      }
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parameter.getProject());
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      subst = subst.put(usedTypeParameter, TypeConversionUtil.typeParameterErasure(usedTypeParameter));
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      final PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }
    return new PsiType[]{psiClass instanceof PsiTypeParameter ? subst.substitute((PsiTypeParameter)psiClass) : elementFactory.createType(psiClass, substitutor)};
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.create.field.from.parameter.text", myName);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter myParameter = findParameterAtCursor(file, editor);
    if (myParameter == null) return false;
    myName = myParameter.getName();
    final PsiType[] types = getTypes(myParameter);
    PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    return
      myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && ((PsiMethod)myParameter.getDeclarationScope()).getBody() != null
      && myParameter.getManager().isInProject(myParameter)
      && types != null
      && types[0].isValid()
      && !isParameterAssignedToField(myParameter)
      && targetClass != null
      && !targetClass.isInterface()
      ;
  }

  static boolean isParameterAssignedToField(final PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false)) {
      if (!(reference instanceof PsiReferenceExpression)) continue;
      final PsiReferenceExpression expression = (PsiReferenceExpression)reference;
      if (!(expression.getParent() instanceof PsiAssignmentExpression)) continue;
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
      if (assignmentExpression.getRExpression() != expression) continue;
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) continue;
      final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
      if (!(element instanceof PsiField)) continue;
      return true;
    }
    return false;
  }

  @Nullable
  static PsiParameter findParameterAtCursor(final PsiFile file, final Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) return null;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) return parameter;
    }
    return null;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.field.from.parameter.family");
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  private static void invoke(final Project project, Editor editor, PsiFile file, boolean isInteractive) {
    final PsiParameter myParameter = findParameterAtCursor(file, editor);
    if (!CodeInsightUtilBase.prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    final PsiType[] types = getTypes(myParameter);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    String fieldNameToCalc;
    boolean isFinalToCalc;
    PsiType type;
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, types[0]);
    String[] names = suggestedNameInfo.names;

    if (isInteractive) {
      List<String> namesList = new ArrayList<String>();
      ContainerUtil.addAll(namesList, names);
      String defaultName = styleManager.propertyNameToVariableName(propertyName, kind);
      if (namesList.contains(defaultName)) {
        Collections.swap(namesList, 0, namesList.indexOf(defaultName));
      }
      else {
        namesList.add(0, defaultName);
      }
      names = ArrayUtil.toStringArray(namesList);

      boolean myBeFinal = method.isConstructor();
      CreateFieldFromParameterDialog dialog = new CreateFieldFromParameterDialog(
        project,
        names,
        targetClass, myBeFinal, types);
      dialog.show();

      if (!dialog.isOK()) return;
      type = dialog.getType();
      if (type == null) return;
      fieldNameToCalc = dialog.getEnteredName();
      isFinalToCalc = dialog.isDeclareFinal();

      suggestedNameInfo.nameChoosen(fieldNameToCalc);
    }
    else {
      isFinalToCalc = !isMethodStatic;
      fieldNameToCalc = names[0];
      type= types[0];
    }

    final boolean isFinal = isFinalToCalc;
    final String fieldName = fieldNameToCalc;
    final PsiType fieldType = type;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          PsiManager psiManager = PsiManager.getInstance(project);
          PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();

          PsiField field = factory.createField(fieldName, fieldType);
          PsiModifierList modifierList = field.getModifierList();
          modifierList.setModifierProperty(PsiModifier.STATIC, isMethodStatic);
          modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

          if (AnnotationUtil.isAnnotated(myParameter, AnnotationUtil.NULLABLE, false)) {
            modifierList.addAfter(factory.createAnnotationFromText("@" + AnnotationUtil.NULLABLE, field), null);
          }

          PsiCodeBlock methodBody = method.getBody();
          if (methodBody == null) return;
          PsiStatement[] statements = methodBody.getStatements();

          Ref<Pair<PsiField, Boolean>> anchorRef = new Ref<Pair<PsiField, Boolean>>();
          int i = findFieldAssignmentAnchor(statements, anchorRef, targetClass, myParameter);
          Pair<PsiField, Boolean> fieldAnchor = anchorRef.get();

          String stmtText = fieldName + " = " + parameterName + ";";
          if (fieldName.equals(parameterName)) {
            @NonNls String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
            stmtText = prefix + stmtText;
          }

          PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
          assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(assignmentStmt);

          if (i == statements.length) {
            methodBody.add(assignmentStmt);
          }
          else {
            methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
          }

          if (fieldAnchor != null) {
            PsiVariable psiVariable = fieldAnchor.getFirst();
            psiVariable.normalizeDeclaration();
          }

          boolean found = false;
          final PsiField[] fields = targetClass.getFields();
          for (PsiField f : fields) {
            if (f.getName().equals(field.getName())) {
              found = true;
              break;
            }
          }

          if (!found) {
            if (fieldAnchor != null) {
              Boolean insertBefore = fieldAnchor.getSecond();
              PsiField inField = fieldAnchor.getFirst();
              if (insertBefore.booleanValue()) {
                targetClass.addBefore(field, inField);
              }
              else {
                targetClass.addAfter(field, inField);
              }
            }
            else {
              targetClass.add(field);
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  static int findFieldAssignmentAnchor(final PsiStatement[] statements, @Nullable final Ref<Pair<PsiField, Boolean>> anchorRef,
                                       final PsiClass targetClass, final PsiParameter myParameter) {
    int i = 0;
    for (; i < statements.length; i++) {
      PsiStatement psiStatement = statements[i];

      if (psiStatement instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement = (PsiExpressionStatement)psiStatement;
        PsiExpression expression = expressionStatement.getExpression();

        if (expression instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
          @NonNls String text = methodCallExpression.getMethodExpression().getText();

          if (text.equals("super") || text.equals("this")) {
            continue;
          }
        }
        else if (expression instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
          PsiExpression lExpression = assignmentExpression.getLExpression();
          PsiExpression rExpression = assignmentExpression.getRExpression();

          if (!(lExpression instanceof PsiReferenceExpression)) break;
          if (!(rExpression instanceof PsiReferenceExpression)) break;

          PsiReferenceExpression lReference = (PsiReferenceExpression)lExpression;
          PsiReferenceExpression rReference = (PsiReferenceExpression)rExpression;

          PsiElement lElement = lReference.resolve();
          PsiElement rElement = rReference.resolve();

          if (!(lElement instanceof PsiField) || ((PsiField)lElement).getContainingClass() != targetClass) break;
          if (!(rElement instanceof PsiParameter)) break;

          if (myParameter.getTextRange().getStartOffset() < rElement.getTextRange().getStartOffset()) {
            if (anchorRef != null) {
              anchorRef.set(Pair.create((PsiField)lElement, Boolean.TRUE));
            }
            break;
          }

          if (anchorRef != null) {
            anchorRef.set(Pair.create((PsiField)lElement, Boolean.FALSE));
          }
          continue;
        }
      }

      break;
    }
    return i;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
