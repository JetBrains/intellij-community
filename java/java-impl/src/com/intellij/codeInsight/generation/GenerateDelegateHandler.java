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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mike
 */
public class GenerateDelegateHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateDelegateHandler");

  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement target = chooseTarget(file, editor, project);
    if (target == null) return;

    final PsiMethodMember[] candidates = chooseMethods(target, file, editor, project);
    if (candidates == null || candidates.length == 0) return;


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          int offset = editor.getCaretModel().getOffset();

          List<PsiGenerationInfo<PsiMethod>> prototypes = new ArrayList<PsiGenerationInfo<PsiMethod>>(candidates.length);
          for (PsiMethodMember candidate : candidates) {
            prototypes.add(generateDelegatePrototype(candidate, target));
          }

          List<PsiGenerationInfo<PsiMethod>> results = GenerateMembersUtil.insertMembersAtOffset(file, offset, prototypes);

          if (!results.isEmpty()) {
            PsiMethod firstMethod = results.get(0).getPsiMember();
            final PsiCodeBlock block = firstMethod.getBody();
            assert block != null;
            final PsiElement first = block.getFirstBodyElement();
            assert first != null;
            editor.getCaretModel().moveToOffset(first.getTextRange().getStartOffset());
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().removeSelection();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static PsiGenerationInfo<PsiMethod> generateDelegatePrototype(PsiMethodMember methodCandidate, PsiElement target) throws IncorrectOperationException {
    PsiMethod method = GenerateMembersUtil.substituteGenericMethod(methodCandidate.getElement(), methodCandidate.getSubstitutor());
    clearMethod(method);

    clearModifiers(method);

    @NonNls StringBuffer call = new StringBuffer();

    PsiModifierList modifierList = null;

    if (method.getReturnType() != PsiType.VOID) {
      call.append("return ");
    }

    if (target instanceof PsiField) {
      PsiField field = (PsiField)target;
      modifierList = field.getModifierList();
      final String name = field.getName();

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (name.equals(parameter.getName())) {
          call.append("this.");
          break;
        }
      }

      call.append(name);
      call.append(".");
    }
    else if (target instanceof PsiMethod) {
      PsiMethod m = (PsiMethod)target;
      modifierList = m.getModifierList();
      call.append(m.getName());
      call.append("().");
    }

    call.append(method.getName());
    call.append("(");
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int j = 0; j < parameters.length; j++) {
      PsiParameter parameter = parameters[j];
      if (j > 0) call.append(",");
      call.append(parameter.getName());
    }
    call.append(");");

    final PsiManager psiManager = method.getManager();
    PsiStatement stmt = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createStatementFromText(call.toString(), method);
    stmt = (PsiStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(stmt);
    method.getBody().add(stmt);

    if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
    }

    PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);

    final Project project = method.getProject();
    for (PsiAnnotation annotation : methodCandidate.getElement().getModifierList().getAnnotations()) {
      OverrideImplementUtil.annotate(method, annotation.getQualifiedName());
    }

    final PsiClass targetClass = ((PsiMember)target).getContainingClass();
    LOG.assertTrue(targetClass != null);
    PsiMethod overridden = targetClass.findMethodBySignature(method, true);
    if (overridden != null) {
      OverrideImplementUtil.annotateOnOverrideImplement(method, targetClass, overridden);
    }

    return new PsiGenerationInfo<PsiMethod>(method);
  }

  private static void clearMethod(PsiMethod method) throws IncorrectOperationException {
    LOG.assertTrue(!method.isPhysical());
    PsiCodeBlock codeBlock = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createCodeBlock();
    if (method.getBody() != null) {
      method.getBody().replace(codeBlock);
    }
    else {
      method.add(codeBlock);
    }

    final PsiDocComment docComment = method.getDocComment();
    if (docComment != null) {
      docComment.delete();
    }
  }

  private static void clearModifiers(PsiMethod method) throws IncorrectOperationException {
    final PsiElement[] children = method.getModifierList().getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) child.delete();
    }
  }

  @Nullable
  private static PsiMethodMember[] chooseMethods(PsiElement target, PsiFile file, Editor editor, Project project) {
    PsiClassType.ClassResolveResult resolveResult = null;

    if (target instanceof PsiField) {
      resolveResult = PsiUtil.resolveGenericsClassInType(((PsiField)target).getType());
    }
    else if (target instanceof PsiMethod) {
      resolveResult = PsiUtil.resolveGenericsClassInType(((PsiMethod)target).getReturnType());
    }

    if (resolveResult == null || resolveResult.getElement() == null) return null;
    PsiClass targetClass = resolveResult.getElement();
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return null;

    List<PsiMethodMember> methodInstances = new ArrayList<PsiMethodMember>();

    final PsiMethod[] allMethods = targetClass.getAllMethods();
    final Set<MethodSignature> signatures = new HashSet<MethodSignature>();
    Map<PsiClass, PsiSubstitutor> superSubstitutors = new HashMap<PsiClass, PsiSubstitutor>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(target.getProject());
    for (PsiMethod method : allMethods) {
      final PsiClass superClass = method.getContainingClass();
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) continue;
      if (method.isConstructor()) continue;
      PsiSubstitutor superSubstitutor = superSubstitutors.get(superClass);
      if (superSubstitutor == null) {
        superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, targetClass, substitutor);
        superSubstitutors.put(superClass, superSubstitutor);
      }
      PsiSubstitutor methodSubstitutor = GenerateMembersUtil.correctSubstitutor(method, superSubstitutor);
      MethodSignature signature = method.getSignature(methodSubstitutor);
      if (!signatures.contains(signature)) {
        signatures.add(signature);
        if (facade.getResolveHelper().isAccessible(method, target, aClass)) {
          methodInstances.add(new PsiMethodMember(method, methodSubstitutor));
        }
      }
    }

    PsiMethodMember[] result;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      MemberChooser<PsiElementClassMember> chooser = new MemberChooser<PsiElementClassMember>(methodInstances.toArray(new PsiMethodMember[methodInstances.size()]), false, true, project);
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.method.chooser.title"));
      chooser.setCopyJavadocVisible(false);
      chooser.show();

      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return null;

      final List<PsiElementClassMember> list = chooser.getSelectedElements();
      result = list.toArray(new PsiMethodMember[list.size()]);
    }
    else {
      result = new PsiMethodMember[] {methodInstances.get(0)};
    }

    return result;
  }

  public static boolean isApplicable(PsiFile file, Editor editor) {
    ClassMember[] targetElements = getTargetElements(file, editor);
    return targetElements != null && targetElements.length > 0;
  }

  @Nullable
  private static PsiElement chooseTarget(PsiFile file, Editor editor, Project project) {
    PsiElement target = null;
    final PsiElementClassMember[] targetElements = getTargetElements(file, editor);
    if (targetElements == null || targetElements.length == 0) return null;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      MemberChooser<PsiElementClassMember> chooser = new MemberChooser<PsiElementClassMember>(targetElements, false, false, project);
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.target.chooser.title"));
      chooser.setCopyJavadocVisible(false);
      chooser.show();

      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return null;

      final List<PsiElementClassMember> selectedElements = chooser.getSelectedElements();

      if (selectedElements != null && selectedElements.size() > 0) target = selectedElements.get(0).getElement();
    }
    else {
      target = targetElements[0].getElement();
    }
    return target;
  }

  @Nullable
  private static PsiElementClassMember[] getTargetElements(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return null;

    List<PsiElementClassMember> result = new ArrayList<PsiElementClassMember>();

    final PsiField[] fields = aClass.getAllFields();
    PsiResolveHelper helper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
    for (PsiField field : fields) {
      final PsiType type = field.getType();
      if (helper.isAccessible(field, aClass, aClass) && type instanceof PsiClassType) {
        result.add(new PsiFieldMember(field));
      }
    }

    final PsiMethod[] methods = aClass.getAllMethods();
    for (PsiMethod method : methods) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName())) continue;
      final PsiType returnType = method.getReturnType();
      if (returnType != null && PropertyUtil.isSimplePropertyGetter(method) && helper.isAccessible(method, aClass, aClass) &&
          returnType instanceof PsiClassType) {
        result.add(new PsiMethodMember(method));
      }
    }

    return result.toArray(new PsiElementClassMember[result.size()]);
  }

}
