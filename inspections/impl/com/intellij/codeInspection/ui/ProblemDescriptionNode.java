package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  public static final Icon INFO = IconLoader.getIcon("/compiler/information.png");
  private RefEntity myElement;
  private CommonProblemDescriptor myDescriptor;
  private boolean myReplaceProblemDescriptorTemplateMessage;

  public ProblemDescriptionNode(RefEntity element, CommonProblemDescriptor descriptor, boolean isReplaceProblemDescriptorTemplateMessage) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
    myReplaceProblemDescriptorTemplateMessage = isReplaceProblemDescriptorTemplateMessage;
  }

  public RefEntity getElement() { return myElement; }
  public CommonProblemDescriptor getDescriptor() { return myDescriptor; }

  public Icon getIcon(boolean expanded) {
    return INFO;
  }

  public int getProblemCount() {
    return 1;
  }

  public boolean isValid() {
    if (myElement instanceof RefElement && !((RefElement)myElement).isValid()) return false;
    if (myDescriptor instanceof ProblemDescriptor) {
      final PsiElement psiElement = ((ProblemDescriptor)myDescriptor).getPsiElement();
      return psiElement != null && psiElement.isValid();
    }
    return true;
  }

  public String toString() {
    return isValid() ? renderDescriptionMessage(myDescriptor, myReplaceProblemDescriptorTemplateMessage) : "";
  }

  private static String renderDescriptionMessage(CommonProblemDescriptor descriptor, boolean isReplaceProblemDescriptorTemplateMessage) {
    PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    @NonNls String message = descriptor.getDescriptionTemplate();

    if (psiElement != null && psiElement.isValid() && message != null) {
      message = message.replaceAll("<[^>]*>", "");
      if (isReplaceProblemDescriptorTemplateMessage){
        message = StringUtil.replace(message, "#ref", psiElement.getText());
      } else {
        final int endIndex = message.indexOf("#end");
        if (endIndex > 0){
          message = message.substring(0, endIndex);
        }
      }
      message = StringUtil.replace(message, "#loc", "");
      message = XmlUtil.unescape(message);
      return message;
    }
    return "";
  }
}
