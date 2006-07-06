package com.intellij.codeInsight.generation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersUtil");

  private GenerateMembersUtil() {
  }

  public static Object[] insertMembersAtOffset(PsiFile file, int offset, Object[] memberPrototypes) throws IncorrectOperationException {
    if (memberPrototypes.length == 0) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    PsiElement anchor = findAnchor(file, offset);
    if (anchor == null) return null;
    PsiClass aClass = (PsiClass) anchor.getParent();

    PsiJavaToken lBrace = aClass.getLBrace();
    if (lBrace == null) {
      anchor = null;
    } else {
      PsiJavaToken rBrace = aClass.getRBrace();
      if (!isChildInRange(anchor, lBrace.getNextSibling(), rBrace)) {
        anchor = null;
      }
    }

    if (anchor instanceof PsiWhiteSpace) {
      anchor = anchor.getNextSibling();
    }

    // Q: shouldn't it be somewhere in PSI?
    {
      PsiElement element = anchor;
      while (true) {
        if (element == null) break;
        if (element instanceof PsiField || element instanceof PsiMethod || element instanceof PsiClassInitializer) break;
        element = element.getNextSibling();
      }
      if (element instanceof PsiField) {
        PsiField field = (PsiField) element;
        if (!field.getTypeElement().getParent().equals(field)) {
          field.normalizeDeclaration();
          anchor = field;
        }
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  private static boolean isChildInRange(PsiElement child, PsiElement first, PsiJavaToken last) {
    if (child.equals(first)) return true;
    while (true) {
      if (child.equals(first)) return false; // before first
      if (child.equals(last)) return true;
      child = child.getNextSibling();
      if (child == null) return false;
    }
  }

  public static Object[] insertMembersBeforeAnchor(PsiClass aClass, PsiElement anchor, Object[] memberPrototypes)
      throws IncorrectOperationException {
    List<Object> newMembersList = new ArrayList<Object>();

    boolean before = true;
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(aClass.getProject());
    for (Object memberPrototype : memberPrototypes) {
      if (memberPrototype instanceof PsiElement) {
        PsiElement prototype = (PsiElement)memberPrototype;

        PsiElement newMember = insert(aClass, prototype, anchor, before);
        newMember = codeStyleManager.shortenClassReferences(newMember);

        newMembersList.add(newMember);
        anchor = newMember;
        before = false;
      }
      else if (memberPrototype instanceof TemplateGenerationInfo) {
        TemplateGenerationInfo info = (TemplateGenerationInfo)memberPrototype;
        info.element = insert(aClass, info.element, anchor, before);
        newMembersList.add(info);
        anchor = info.element;
        before = false;
      }
      else {
        LOG.error("unknown element: " + memberPrototype);
      }
    }

    return newMembersList.toArray();
  }

  public static void positionCaret(Editor editor, PsiElement firstMember, boolean toEditBody) {
    LOG.assertTrue(firstMember.isValid());

    if (toEditBody) {
      PsiMethod method = (PsiMethod) firstMember;
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        PsiElement l = body.getFirstBodyElement();
        while (l instanceof PsiWhiteSpace) l = l.getNextSibling();
        PsiElement r = body.getLastBodyElement();
        while (r instanceof PsiWhiteSpace) r = r.getPrevSibling();
        LOG.assertTrue(l != null && r != null);
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

  private static PsiElement insert(PsiElement parent, PsiElement element, PsiElement anchor, boolean before)
      throws IncorrectOperationException {
    if (anchor != null) {
      return before ? parent.addBefore(element, anchor) : parent.addAfter(element, anchor);
    }
    else {
      return parent.add(element);
    }
  }

  private static PsiElement findAnchor(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    while (true) {
      if (element instanceof PsiFile) return null;
      PsiElement parent = element.getParent();
      if (parent instanceof PsiClass && !(parent instanceof PsiTypeParameter)) {
        if (((PsiClass)parent).isEnum()) {
          PsiElement lastChild = null;
          PsiElement[] children = parent.getChildren();
          for (PsiElement child : children) {
            if (child instanceof PsiJavaToken && ";".equals(child.getText())) {
              lastChild = child;
              break;
            }
            else if ((child instanceof PsiJavaToken && ",".equals(child.getText())) || child instanceof PsiEnumConstant) {
              lastChild = child;
              continue;
            }
          }
          if (lastChild != null) {
            int adjustedOffset = lastChild.getTextRange().getEndOffset();
            if (offset < adjustedOffset) return findAnchor(file, adjustedOffset);
          }
        }
        break;
      }
      element = parent;
    }
    return element;
  }

  public static PsiMethod substituteGenericMethod(PsiMethod method, PsiSubstitutor substitutor) {
    Project project = method.getProject();
    PsiElementFactory factory = method.getManager().getElementFactory();
    PsiMethod newMethod;
    boolean isRaw = PsiUtil.isRawSubstitutor(method, substitutor);

    PsiTypeParameter[] typeParams = method.getTypeParameters();
    try {
      PsiType returnType = method.getReturnType();

      if (method.isConstructor()) {
        newMethod = factory.createConstructor();
        newMethod.getNameIdentifier().replace(factory.createIdentifier(method.getName()));
      }
      else {
        newMethod = factory.createMethod(method.getName(), substituteType(substitutor, returnType, isRaw));
      }

      RefactoringUtil.setVisibility(newMethod.getModifierList(), VisibilityUtil.getVisibilityModifier(method.getModifierList()));

      PsiDocComment docComment = ((PsiMethod)method.getNavigationElement()).getDocComment();
      if (docComment != null) {
        newMethod.addAfter(docComment, null);
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      Map<PsiType,Pair<String,Integer>> m = new HashMap<PsiType, Pair<String,Integer>>();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        final PsiType parameterType = parameter.getType();
        PsiType substituted = substituteType(substitutor, parameterType, isRaw);
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
        newMethod.getParameterList().add(newParameter);
      }


      for (PsiTypeParameter typeParam : typeParams) {
        if (substitutor.substitute(typeParam) != null) newMethod.getTypeParameterList().add(typeParam);
      }

      PsiClassType[] thrownTypes = method.getThrowsList().getReferencedTypes();
      for (PsiClassType thrownType : thrownTypes) {
        newMethod.getThrowsList().add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType, isRaw)));
      }
      return newMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return method;
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor, final PsiType type, final boolean isRaw) {
    return isRaw ? TypeConversionUtil.erasure(type) : substitutor.substitute(type);
  }

  public static PsiSubstitutor correctSubstitutor(PsiMethod method, PsiSubstitutor substitutor) {
    PsiClass hisClass = method.getContainingClass();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length > 0) {
      if (PsiUtil.isRawSubstitutor(hisClass, substitutor)) {
        for (PsiTypeParameter typeParameter : typeParameters) {
          substitutor = substitutor.put(typeParameter, null);
        }
      }
    }
    return substitutor;
  }
}
