package com.intellij.ide.actions;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.CodeInsightUtilBase;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaQualifiedNameProvider implements QualifiedNameProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.JavaQualifiedNameProvider");

  @Nullable
  public PsiElement adjustElementToCopy(final PsiElement element) {
    if (element != null && !(element instanceof PsiMember) && element.getParent() instanceof PsiMember) {
      return element.getParent();
    }
    return null;
  }

  @Nullable
  public String getQualifiedName(PsiElement element) {
    element = getMember(element);
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      return member.getContainingClass().getQualifiedName() + "#" + member.getName();
    }
    return null;
  }

  public PsiElement qualifiedNameToElement(final String fqn, final Project project) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return aClass;
    }
    final int endIndex = fqn.indexOf('#');
    if (endIndex == -1) return null;
    String className = fqn.substring(0, endIndex);
    if (className == null) return null;
    aClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (aClass == null) return null;
    String memberName = fqn.substring(endIndex + 1);
    PsiField field = aClass.findFieldByName(memberName, false);
    if (field != null) {
      return field;
    }
    PsiMethod[] methods = aClass.findMethodsByName(memberName, false);
    if (methods.length == 0) return null;
    return methods[0];
  }

  public void insertQualifiedName(String fqn, final PsiElement element, final Editor editor, final Project project) {
    if (!(element instanceof PsiMember)) return;
    PsiMember targetElement = (PsiMember) element;
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();

    final PsiFile file = documentManager.getPsiFile(document);

    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if (elementAtCaret == null) return;

    fqn = fqn.replace('#', '.');
    String toInsert;
    String suffix = "";
    PsiElement elementToInsert = targetElement;
    if (targetElement instanceof PsiMethod && PsiUtil.isInsideJavadocComment(elementAtCaret)) {
      // use fqn#methodName(ParamType)
      PsiMethod method = (PsiMethod)targetElement;
      PsiClass aClass = method.getContainingClass();
      String className = aClass == null ? "" : aClass.getQualifiedName();
      toInsert = className == null ? "" : className;
      if (toInsert.length() != 0) toInsert += "#";
      toInsert += method.getName() + "(";
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i != 0) toInsert += ", ";
        toInsert += parameter.getType().getCanonicalText();
      }
      toInsert += ")";
    }
    else if (targetElement == null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiLiteralExpression.class, PsiComment.class) != null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiJavaFile.class) == null) {
      toInsert = fqn;
    }
    else {
      toInsert = targetElement.getName();
      if (targetElement instanceof PsiMethod) {
        suffix = "()";
        if (((PsiMethod)targetElement).isConstructor()) {
          targetElement = targetElement.getContainingClass();
        }
      }
      else if (targetElement instanceof PsiClass) {
        if (isAfterNew(file, elementAtCaret)) {
          // pasting reference to default constructor of the class after new
          suffix = "()";
        }
        else if (toInsert != null && toInsert.length() != 0 && Character.isJavaIdentifierPart(toInsert.charAt(toInsert.length()-1)) && Character.isJavaIdentifierPart(elementAtCaret.getText().charAt(0))) {
          //separate identifiers with space
          suffix = " ";
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression expression;
      try {
        expression = factory.createExpressionFromText(toInsert + suffix, elementAtCaret);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }
      final PsiReferenceExpression referenceExpression = expression instanceof PsiMethodCallExpression
                                                         ? ((PsiMethodCallExpression)expression).getMethodExpression()
                                                         : expression instanceof PsiReferenceExpression
                                                           ? (PsiReferenceExpression)expression
                                                           : null;
      if (referenceExpression == null) {
        toInsert = fqn;
      }
      else if (referenceExpression.advancedResolve(true).getElement() != targetElement) {
        try {
          referenceExpression.bindToElement(targetElement);
        }
        catch (IncorrectOperationException e) {
          // failed to bind
        }
        if (referenceExpression.advancedResolve(true).getElement() != targetElement) {
          toInsert = fqn;
        }
      }
    }
    if (toInsert == null) toInsert = "";

    document.insertString(offset, toInsert+suffix);
    documentManager.commitAllDocuments();
    int endOffset = offset + toInsert.length() + suffix.length();
    RangeMarker rangeMarker = document.createRangeMarker(endOffset, endOffset);
    elementAtCaret = file.findElementAt(offset);

    if (elementAtCaret != null && elementAtCaret.isValid()) {
      try {
        shortenReference(elementAtCaret, targetElement);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(file);
    try {
      CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    int caretOffset = rangeMarker.getEndOffset();
    if (elementToInsert instanceof PsiMethod && ((PsiMethod)elementToInsert).getParameterList().getParametersCount() != 0 && StringUtil.endsWithChar(suffix,')')) {
      caretOffset --;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  @Nullable
  private static PsiElement getMember(final PsiElement element) {
    if (element instanceof PsiMember) return element;
    if (element instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)element).resolve();
      if (resolved instanceof PsiMember) return resolved;
    }
    if (!(element instanceof PsiIdentifier)) return null;
    final PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember)resolved;
      }
    }
    else if (parent instanceof PsiMember) {
      member = (PsiMember)parent;
    }
    else {
      //todo show error
      //return;
    }
    return member;
  }

  private static boolean isAfterNew(PsiFile file, PsiElement elementAtCaret) {
    PsiElement prevSibling = elementAtCaret.getPrevSibling();
    if (prevSibling == null) return false;
    int offset = prevSibling.getTextRange().getStartOffset();
    PsiElement prevElement = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(prevElement, PsiNewExpression.class) != null;
  }

  private static void shortenReference(PsiElement element, PsiMember elementToInsert) throws IncorrectOperationException {
    while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
      element = element.getParent();
      if (element == null) return;
    }
    if (element instanceof PsiJavaCodeReferenceElement && elementToInsert != null) {
      try {
        element = ((PsiJavaCodeReferenceElement)element).bindToElement(elementToInsert);
      }
      catch (IncorrectOperationException e) {
        // failed to bind
      }
    }
    final JavaCodeStyleManager codeStyleManagerEx = JavaCodeStyleManager.getInstance(element.getProject());
    codeStyleManagerEx.shortenClassReferences(element, JavaCodeStyleManager.UNCOMPLETE_CODE);
  }
}
