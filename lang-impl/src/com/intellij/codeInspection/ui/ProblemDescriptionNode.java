package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  public static final Icon INFO = IconLoader.getIcon("/compiler/information.png");
  protected RefEntity myElement;
  private CommonProblemDescriptor myDescriptor;
  private boolean myReplaceProblemDescriptorTemplateMessage;
  protected DescriptorProviderInspection myTool;

  public ProblemDescriptionNode(final Object userObject,
                                final boolean replaceProblemDescriptorTemplateMessage,
                                final DescriptorProviderInspection tool) {
    super(userObject);
    myReplaceProblemDescriptorTemplateMessage = replaceProblemDescriptorTemplateMessage;
    myTool = tool;
  }

  public ProblemDescriptionNode(RefEntity element,
                                CommonProblemDescriptor descriptor,
                                boolean isReplaceProblemDescriptorTemplateMessage,
                                DescriptorProviderInspection descriptorProviderInspection) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
    myReplaceProblemDescriptorTemplateMessage = isReplaceProblemDescriptorTemplateMessage;
    myTool = descriptorProviderInspection;
  }

  @Nullable
  public RefEntity getElement() { return myElement; }
  @Nullable
  public CommonProblemDescriptor getDescriptor() { return myDescriptor; }

  public Icon getIcon(boolean expanded) {
    return INFO;
  }

  public int getProblemCount() {
    return 1;
  }

  public boolean isValid() {
    if (myElement instanceof RefElement && !((RefElement)myElement).isValid()) return false;
    final CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor instanceof ProblemDescriptor) {
      final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      return psiElement != null && psiElement.isValid();
    }
    return true;
  }


  public boolean isResolved() {
    return myElement instanceof RefElement && myTool.isElementIgnored((RefElement)myElement);
  }

  public void ignoreElement() {
    myTool.ignoreCurrentElement(getElement());
  }

  public void amnesty() {
    myTool.amnesty(getElement());
  }

  public FileStatus getNodeStatus() {
    if (myElement instanceof RefElement){
      return myTool.getProblemStatus(myDescriptor);
    }
    return FileStatus.NOT_CHANGED;
  }

  public String toString() {
    return renderDescriptionMessage(getDescriptor(), myReplaceProblemDescriptorTemplateMessage);
  }

  private static String renderDescriptionMessage(@Nullable CommonProblemDescriptor descriptor, boolean isReplaceProblemDescriptorTemplateMessage) {
    PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    @NonNls String message = descriptor != null ? descriptor.getDescriptionTemplate().replaceAll("<[^>]*>", "") : "";
    if (isReplaceProblemDescriptorTemplateMessage) {
      message = StringUtil.replace(message, "#ref", psiElement != null && psiElement.isValid() ? psiElement.getText() : "");
    }
    else {
      final int endIndex = message.indexOf("#end");
      if (endIndex > 0) {
        message = message.substring(0, endIndex);
      }
    }
    message = StringUtil.replace(message, "#loc", "");
    message = StringUtil.unescapeXml(message);
    return message;
  }
}
