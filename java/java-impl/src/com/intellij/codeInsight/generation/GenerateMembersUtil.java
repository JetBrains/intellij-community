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

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersUtil");

  private GenerateMembersUtil() {
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(PsiFile file, int offset, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    if (memberPrototypes.isEmpty()) return memberPrototypes;
    final PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) return Collections.emptyList();

    PsiClass aClass = findClassAtOffset(file, leaf);
    if (aClass == null) return Collections.emptyList();
    PsiElement anchor = memberPrototypes.get(0).findInsertionAnchor(aClass, leaf);

    if (anchor instanceof PsiWhiteSpace) {
      final ASTNode spaceNode = anchor.getNode();
      anchor = anchor.getNextSibling();

      assert spaceNode != null;
      if (spaceNode.getStartOffset() <= offset && spaceNode.getStartOffset() + spaceNode.getTextLength() >= offset) {
        final ASTNode singleNewLineWhitespace = JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createWhiteSpaceFromText(spaceNode.getText().substring(0, offset - spaceNode.getStartOffset())).getNode();
        spaceNode.getTreeParent().replaceChild(spaceNode, singleNewLineWhitespace); // See http://jetbrains.net/jira/browse/IDEADEV-12837
      }
    }

    // Q: shouldn't it be somewhere in PSI?
    PsiElement element = anchor;
    while (true) {
      if (element == null) break;
      if (element instanceof PsiField || element instanceof PsiMethod || element instanceof PsiClassInitializer) break;
      element = element.getNextSibling();
    }
    if (element instanceof PsiField) {
      PsiField field = (PsiField) element;
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null && !field.equals(typeElement.getParent())) {
        field.normalizeDeclaration();
        anchor = field;
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersBeforeAnchor(PsiClass aClass, PsiElement anchor, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    boolean before = true;
    for (T memberPrototype : memberPrototypes) {
      memberPrototype.insert(aClass, anchor, before);
      before = false;
      anchor = memberPrototype.getPsiMember();
    }
    return memberPrototypes;
  }

  public static void positionCaret(@NotNull Editor editor, @NotNull PsiElement firstMember, boolean toEditMethodBody) {
    LOG.assertTrue(firstMember.isValid());

    if (toEditMethodBody) {
      PsiMethod method = (PsiMethod) firstMember;
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        PsiElement l = body.getFirstBodyElement();
        while (l instanceof PsiWhiteSpace) l = l.getNextSibling();
        if (l == null) l = body;
        PsiElement r = body.getLastBodyElement();
        while (r instanceof PsiWhiteSpace) r = r.getPrevSibling();
        if (r == null) r = body;

        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();

        editor.getCaretModel().moveToOffset(Math.min(start, end));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (start < end) {
          //Not an empty body
          editor.getSelectionModel().setSelection(start, end);
        }
        return;
      }
    }

    int offset;
    if (firstMember instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) firstMember;
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        offset = method.getTextRange().getStartOffset();
      }
      else {
        offset = body.getLBrace().getTextRange().getEndOffset();
      }
    }
    else {
      offset = firstMember.getTextRange().getStartOffset();
    }

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  public static PsiElement insert(PsiClass aClass, PsiMember member, PsiElement anchor, boolean before) throws IncorrectOperationException {
    if (member instanceof PsiMethod) {
      if (!aClass.isInterface()) {
        final PsiParameter[] parameters = ((PsiMethod)member).getParameterList().getParameters();
        final boolean generateFinals = CodeStyleSettingsManager.getSettings(aClass.getProject()).GENERATE_FINAL_PARAMETERS;
        for (final PsiParameter parameter : parameters) {
          final PsiModifierList modifierList = parameter.getModifierList();
          assert modifierList != null;
          modifierList.setModifierProperty(PsiModifier.FINAL, generateFinals);
        }
      }
    }

    if (anchor != null) {
      return before ? aClass.addBefore(member, anchor) : aClass.addAfter(member, anchor);
    }
    else {
      return aClass.add(member);
    }
  }

  @Nullable
  private static PsiClass findClassAtOffset(PsiFile file, PsiElement leaf) {
    PsiElement element = leaf;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        final PsiClass psiClass = (PsiClass)element;
        if (psiClass.isEnum()) {
          PsiElement lastChild = null;
          for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiJavaToken && ";".equals(child.getText())) {
              lastChild = child;
              break;
            }
            else if (child instanceof PsiJavaToken && ",".equals(child.getText()) || child instanceof PsiEnumConstant) {
              lastChild = child;
            }
          }
          if (lastChild != null) {
            int adjustedOffset = lastChild.getTextRange().getEndOffset();
            if (leaf.getTextRange().getEndOffset() <= adjustedOffset) return findClassAtOffset(file, file.findElementAt(adjustedOffset));
          }
        }
        return psiClass;
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiMethod substituteGenericMethod(PsiMethod method, final PsiSubstitutor substitutor) {
    Project project = method.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();

    try {
      PsiType returnType = method.getReturnType();

      PsiMethod newMethod;
      if (method.isConstructor()) {
        newMethod = factory.createConstructor();
        newMethod.getNameIdentifier().replace(factory.createIdentifier(method.getName()));
      }
      else {
        newMethod = factory.createMethod(method.getName(), substituteType(substitutor, returnType));
      }

      VisibilityUtil.setVisibility(newMethod.getModifierList(), VisibilityUtil.getVisibilityModifier(method.getModifierList()));

      PsiElement navigationElement = method.getNavigationElement();
      PsiDocComment docComment = ((PsiDocCommentOwner)navigationElement).getDocComment();
      if (docComment != null) {
        newMethod.addAfter(docComment, null);
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      Map<PsiType,Pair<String,Integer>> m = new HashMap<PsiType, Pair<String,Integer>>();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        final PsiType parameterType = parameter.getType();
        PsiType substituted = substituteType(substitutor, parameterType);
        @NonNls String paramName = parameter.getName();
        final String[] baseSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameterType).names;
        boolean isBaseNameGenerated = false;
        for (String s : baseSuggestions) {
          if (s.equals(paramName)) {
            isBaseNameGenerated = true;
            break;
          }
        }
        
        if (paramName == null || isBaseNameGenerated && !substituted.equals(parameterType)) {
          Pair<String, Integer> pair = m.get(substituted);
          if (pair != null) {
            paramName = pair.first + pair.second;
            m.put(substituted, Pair.create(pair.first, pair.second.intValue() + 1));
          }
          else {
            String[] names = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, substituted).names;
            if (names.length > 0) {
              paramName = names[0];
            } else paramName = "p" + i;

            m.put(substituted, new Pair<String, Integer>(paramName, 1));
          }
        }

        if (paramName == null) paramName = "p" + i;

        PsiParameter newParameter = factory.createParameter(paramName, substituted);
        if (parameter.getLanguage() == StdLanguages.JAVA) {
          newParameter.getModifierList().replace(parameter.getModifierList());
        }
        newMethod.getParameterList().add(newParameter);
      }

      for (PsiTypeParameter typeParam : method.getTypeParameters()) {
        final PsiElement copy = typeParam.copy();
        final Map<PsiElement, PsiElement> replacementMap = new HashMap<PsiElement, PsiElement>();
        copy.accept(new JavaRecursiveElementWalkingVisitor(){
          @Override
          public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            final PsiElement resolve = reference.resolve();
            if (resolve instanceof PsiTypeParameter) {
              replacementMap.put(reference, factory.createReferenceElementByType((PsiClassType)substitutor.substitute((PsiTypeParameter)resolve)));
            }
          }
        });
        newMethod.getTypeParameterList().add(RefactoringUtil.replaceElementsWithMap(copy, replacementMap));
      }

      PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
      for (PsiClassType thrownType : thrownTypes) {
        newMethod.getThrowsList().add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType)));
      }
      return newMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return method;
    }
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor, final PsiType type) {
    final PsiType psiType = substitutor.substitute(type);
    if (psiType != null) return psiType;
    return TypeConversionUtil.erasure(type);
  }

  public static PsiSubstitutor correctSubstitutor(PsiMethod method, PsiSubstitutor substitutor) {
    PsiClass hisClass = method.getContainingClass();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length > 0) {
      if (PsiUtil.isRawSubstitutor(hisClass, substitutor)) {
        substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, typeParameters);
      }
    }
    return substitutor;
  }

  public static boolean isChildInRange(PsiElement child, PsiElement first, PsiElement last) {
    if (child.equals(first)) return true;
    while (true) {
      if (child.equals(first)) return false; // before first
      if (child.equals(last)) return true;
      child = child.getNextSibling();
      if (child == null) return false;
    }
  }

  public static boolean shouldAddOverrideAnnotation(PsiElement context, boolean interfaceMethod) {
    CodeStyleSettings style = CodeStyleSettingsManager.getSettings(context.getProject());
    if (!style.INSERT_OVERRIDE_ANNOTATION) return false;
    
    if (interfaceMethod) return PsiUtil.isLanguageLevel6OrHigher(context);
    return PsiUtil.isLanguageLevel5OrHigher(context);
  }

  public static void setupGeneratedMethod(PsiMethod method) {
    PsiClass base = method.getContainingClass().getSuperClass();
    PsiMethod overridden = base == null ? null : base.findMethodBySignature(method, true);

    if (overridden == null) {
      CreateFromUsageUtils.setupMethodBody(method, method.getContainingClass());
      return;
    }

    OverrideImplementUtil.setupMethodBody(method, overridden, method.getContainingClass());
    OverrideImplementUtil.annotateOnOverrideImplement(method, base, overridden);
  }
}
