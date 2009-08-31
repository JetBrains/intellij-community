package com.intellij.ui;

import com.intellij.psi.*;
import com.intellij.openapi.editor.Document;
import com.intellij.util.Function;

import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class JavaReferenceEditorUtil {
  private JavaReferenceEditorUtil() {
  }

  public static ReferenceEditorWithBrowseButton createReferenceEditorWithBrowseButton(final ActionListener browseActionListener,
                                                                                      final String text,
                                                                                      final PsiManager manager,
                                                                                      final boolean toAcceptClasses) {
    return new ReferenceEditorWithBrowseButton(browseActionListener, manager.getProject(),
                                               new Function<String,Document>() {
      public Document fun(final String s) {
        return createDocument(s, manager, toAcceptClasses);
      }
    }, text);
  }

  public static Document createDocument(final String text, PsiManager manager, boolean isClassesAccepted) {
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
    final JavaCodeFragment fragment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public static Document createTypeDocument(final String text, PsiManager manager) {
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
    final JavaCodeFragment fragment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeCodeFragment(text, defaultPackage, false, true, false);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }
}
