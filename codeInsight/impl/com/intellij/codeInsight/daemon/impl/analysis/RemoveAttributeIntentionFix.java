package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: Nov 29, 2007
* Time: 11:14:30 PM
* To change this template use File | Settings | File Templates.
*/
class RemoveAttributeIntentionFix implements IntentionAction {
  private final String myLocalName;
  private final XmlAttribute myAttribute;

  public RemoveAttributeIntentionFix(final String localName, final @NotNull XmlAttribute attribute) {
    myLocalName = localName;
    myAttribute = attribute;
  }

  @NotNull
  public String getText() {
    return XmlErrorMessages.message("remove.attribute.quickfix.text", myLocalName);
  }

  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.attribute.quickfix.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myAttribute.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiElement next = findNextAttribute(myAttribute);
    myAttribute.delete();

    if (next != null) {
      editor.getCaretModel().moveToOffset(next.getTextRange().getStartOffset());
    }
  }

  private static PsiElement findNextAttribute(final XmlAttribute attribute) {
    PsiElement nextSibling = attribute.getNextSibling();
    while (nextSibling != null) {
      if (nextSibling instanceof XmlAttribute) return nextSibling;
      nextSibling =  nextSibling.getNextSibling();
    }
    return null;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
