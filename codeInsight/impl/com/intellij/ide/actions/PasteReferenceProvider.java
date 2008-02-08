package com.intellij.ide.actions;

import com.intellij.ide.PasteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class PasteReferenceProvider implements PasteProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.PasteReferenceProvider");

  public void performPaste(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) return;

    final String fqn = getCopiedFqn();
    PsiMember element = fqnToElement(project, fqn);
    insert(fqn, element, editor);
  }

  public boolean isPastePossible(DataContext dataContext) {
    return isPasteEnabled(dataContext);
  }

  public boolean isPasteEnabled(DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    return project != null && editor != null && getCopiedFqn() != null;
  }

  private static void insert(final String fqn, final PsiMember element, final Editor editor) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(editor.getProject());
    documentManager.commitDocument(editor.getDocument());
    final PsiFile file = documentManager.getPsiFile(editor.getDocument());
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final Project project = editor.getProject();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              Document document = editor.getDocument();
              documentManager.doPostponedOperationsAndUnblockDocument(document);
              documentManager.commitDocument(document);
              EditorModificationUtil.deleteSelectedText(editor);
              doInsert(fqn, element, editor, project);
            }
            catch (IncorrectOperationException e1) {
              LOG.error(e1);
            }
          }
        });
      }
    }, IdeBundle.message("command.pasting.reference"), null);
  }

  private static void doInsert(String fqn,
                               PsiMember targetElement,
                               final Editor editor,
                               final Project project) throws IncorrectOperationException {
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
      final PsiExpression expression = factory.createExpressionFromText(toInsert + suffix, elementAtCaret);
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
      shortenReference(elementAtCaret, targetElement);
    }
    CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(file);
    CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);

    int caretOffset = rangeMarker.getEndOffset();
    if (elementToInsert instanceof PsiMethod && ((PsiMethod)elementToInsert).getParameterList().getParametersCount() != 0 && StringUtil.endsWithChar(suffix,')')) {
      caretOffset --;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  @Nullable
  private static String getCopiedFqn() {
    final Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) return null;
    try {
      return (String)contents.getTransferData(CopyReferenceAction.OUR_DATA_FLAVOR);
    }
    catch (UnsupportedFlavorException e) {
    }
    catch (IOException e) {
    }
    return null;
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

  @Nullable
  private static PsiMember fqnToElement(final Project project, final String fqn) {
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
}
