/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  private static final Icon INFO = IconLoader.getIcon("/compiler/information.png");
  private static final Icon ERROR = IconLoader.getIcon("/compiler/error.png");
  private static final Icon WARNING = IconLoader.getIcon("/compiler/warning.png");
  protected RefEntity myElement;
  private CommonProblemDescriptor myDescriptor;
  protected DescriptorProviderInspection myTool;

  public ProblemDescriptionNode(final Object userObject, final DescriptorProviderInspection tool) {
    super(userObject);
    myTool = tool;
  }

  public ProblemDescriptionNode(RefEntity element,
                                CommonProblemDescriptor descriptor,
                                DescriptorProviderInspection descriptorProviderInspection) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
    myTool = descriptorProviderInspection;
  }

  @Nullable
  public RefEntity getElement() {
    return myElement;
  }

  @Nullable
  public CommonProblemDescriptor getDescriptor() {
    return myDescriptor;
  }

  public Icon getIcon(boolean expanded) {
    if (myDescriptor instanceof ProblemDescriptorImpl) {
      ProblemHighlightType problemHighlightType = ((ProblemDescriptorImpl)myDescriptor).getHighlightType();
      if (problemHighlightType == ProblemHighlightType.ERROR) return ERROR;
      if (problemHighlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) return WARNING;
    }
    return INFO;
  }

  public int getProblemCount() {
    return 1;
  }

  public boolean isValid() {
    if (myElement instanceof RefElement && !myElement.isValid()) return false;
    final CommonProblemDescriptor descriptor = getDescriptor();
    if (descriptor instanceof ProblemDescriptor) {
      final PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
      return psiElement != null && psiElement.isValid();
    }
    return true;
  }


  public boolean isResolved() {
    return myElement instanceof RefElement && myTool.isElementIgnored(myElement);
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
    return renderDescriptionMessage(getDescriptor()).replaceAll("<[^>]*>", "");
  }

  public static String renderDescriptionMessage(CommonProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    String message = descriptor.getDescriptionTemplate();

    // no message. Should not be the case if inspection correctly implemented.
    // noinspection ConstantConditions
    if (message == null) return "";

    message = StringUtil.replace(message, "<code>", "'");
    message = StringUtil.replace(message, "</code>", "'");
    message = StringUtil.replace(message, "#loc", "");
    if (message.contains("#ref")) {
      String ref = extractHighlightedText(descriptor, psiElement);
      message = StringUtil.replace(message, "#ref", ref);
    }
    final int endIndex = message.indexOf("#end");
    if (endIndex > 0) {
      message = message.substring(0, endIndex);
    }

    message = StringUtil.unescapeXml(message).trim();
    return message;
  }

  public static String extractHighlightedText(CommonProblemDescriptor descriptor, PsiElement psiElement) {
    if (psiElement == null || !psiElement.isValid()) return "";
    String ref = psiElement.getText();
    if (descriptor instanceof ProblemDescriptorImpl) {
      TextRange textRange = ((ProblemDescriptorImpl)descriptor).getTextRange();
      final TextRange elementRange = psiElement.getTextRange();
      if (textRange != null && elementRange != null) {
        textRange = textRange.shiftRight(-elementRange.getStartOffset());
        if (textRange.getStartOffset() >= 0 && textRange.getEndOffset() <= ref.length()) {
          ref = textRange.substring(ref);
        }
      }
    }
    ref = ref.replaceAll("\n", " ").trim();
    if (ref.length() > 100) {
      ref = ref.substring(0, 100).trim() + "...";
    }
    return ref;
  }
}
