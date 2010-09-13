package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;

/**
 * User: anna
 * Date: Sep 10, 2010
 */
public abstract class MoveParameterAction extends AnAction{
  private final boolean myLeft;
  private static final Logger LOG = Logger.getInstance("#" + MoveParameterAction.class.getName());

  public MoveParameterAction(boolean left) {
    super();
    myLeft = left;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final PsiElement psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    LOG.assertTrue(psiElement != null);
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    LanguageChangeSignatureDetectors.INSTANCE.forLanguage(psiElement.getLanguage()).moveParameter(psiElement, editor, myLeft);
  }


  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    final DataContext dataContext = e.getDataContext();
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      final PsiElement psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      if (psiElement != null) {
        final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(psiElement.getLanguage());
        if (detector != null) {
          presentation.setEnabled(detector.isMoveParameterAvailable(psiElement, myLeft));
        }
      }
    }
  }
}
