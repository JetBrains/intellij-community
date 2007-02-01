package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class CreateClassFromUsageFix extends CreateClassFromUsageBaseFix {

  public CreateClassFromUsageFix(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    super(kind, refElement);
  }

  public String getText(String varName) {
    return QuickFixBundle.message("create.class.from.usage.text", StringUtil.capitalize(myKind.getDescription()), varName);
  }


  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    final PsiJavaCodeReferenceElement element = getRefElement();
    assert element != null;
    final String superClassName = getSuperClassName(element);
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          PsiJavaCodeReferenceElement refElement = element;
          final PsiClass aClass = CreateFromUsageUtils.createClass(refElement, myKind, superClassName);
          if (aClass == null) return;
          try {
            refElement = (PsiJavaCodeReferenceElement)refElement.bindToElement(aClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

          OpenFileDescriptor descriptor = new OpenFileDescriptor(refElement.getProject(), aClass.getContainingFile().getVirtualFile(),
                                                                 aClass.getTextOffset());
          FileEditorManager.getInstance(aClass.getProject()).openTextEditor(descriptor, true);
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }

}
