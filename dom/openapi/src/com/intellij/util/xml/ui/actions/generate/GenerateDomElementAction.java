package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public class GenerateDomElementAction extends BaseGenerateAction {

  protected final GenerateDomElementProvider myProvider;

  public GenerateDomElementAction(final GenerateDomElementProvider generateProvider) {
    super(new CodeInsightActionHandler() {
      public void invoke(final Project project, final Editor editor, final PsiFile file) {
        new WriteCommandAction(project, file) {
          protected void run(final Result result) throws Throwable {
            final DomElement element = generateProvider.generate(project, editor, file);
            generateProvider.navigate(element);
          }
        }.execute();
      }

      public boolean startInWriteAction() {
        return false;
      }
    });

    getTemplatePresentation().setDescription(generateProvider.getDescription());
    getTemplatePresentation().setText(generateProvider.getDescription());

    myProvider = generateProvider;
  }

  @Nullable
  protected DomElement getContextElement(final Project project, final Editor editor, final PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag != null) {
      return DomManager.getDomManager(project).getDomElement(tag);
    }
    return null;
  }

  protected boolean isValidForFile(final Project project, final Editor editor, final PsiFile file) {
    final DomElement element = getContextElement(project, editor, file);
    return element != null && myProvider.isAvailableForElement(element);
  }
}
