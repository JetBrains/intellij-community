/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection");
  @NonNls private static final String SHORT_NAME = "JavadocReference";


  private ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template) {
    return InspectionManager.getInstance(element.getProject())
      .createProblemDescriptor(element, template, (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    return checkMember(psiMethod);
  }

  private ProblemDescriptor[] checkMember(final PsiDocCommentOwner docCommentOwner) {
    ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    final PsiDocComment docComment = docCommentOwner.getDocComment();
    if (docComment == null) return null;

    final String[] refMessage = new String[]{null};
    final PsiJavaCodeReferenceElement[] references = new PsiJavaCodeReferenceElement[]{null};
    docCommentOwner.accept(getVisitor(references, refMessage, docCommentOwner, problems));
    if (refMessage[0] != null) {
      problems.add(createDescriptor(references[0], refMessage[0]));
    }

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    return checkMember(aClass);
  }


  private PsiElementVisitor getVisitor(final PsiJavaCodeReferenceElement[] references,
                                       final String[] refMessage,
                                       final PsiElement context,
                                       final ArrayList<ProblemDescriptor> problems) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolved = reference.resolve();
        if (resolved == null) {
          refMessage[0] = InspectionsBundle.message("inspection.javadoc.problem.descriptor8", "<code>" + reference.getText() + "</code>");
          references[0] = reference;
        }
      }

      public void visitDocTag(PsiDocTag tag) {
        super.visitDocTag(tag);
        visitRefInDocTag(tag, tag.getManager().getJavadocManager(), context, problems);
      }

      public void visitElement(PsiElement element) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          //do not visit method javadoc twice
          if (!(child instanceof PsiDocCommentOwner)) {
            child.accept(this);
          }
        }
      }

      public void visitDocComment(PsiDocComment comment) {
        super.visitDocComment(comment);
        final PsiElement[] descriptionElements = comment.getDescriptionElements();
        for (PsiElement element : descriptionElements) {
          element.accept(this);
        }
      }
    };
  }

  public boolean visitRefInDocTag(final PsiDocTag tag,
                                  final JavadocManager manager,
                                  final PsiElement context,
                                  ArrayList<ProblemDescriptor> problems) {
    String tagName = tag.getName();
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return true;
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return true;
    String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null){
      problems.add(createDescriptor(value, message));
    }
    final PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      if (element == null) {
        final int textOffset = value.getTextOffset();

        if (textOffset != value.getTextRange().getEndOffset()) {
          if (problems == null) problems = new ArrayList<ProblemDescriptor>();
          final PsiDocTagValue valueElement = tag.getValueElement();
          if (valueElement != null) {
            problems.add(createDescriptor(valueElement, InspectionsBundle.message("inspection.javadoc.problem.descriptor8", "<code>" +
                                                                                                                            new String(value
                                                                                                                              .getContainingFile().textToCharArray(),
                                                                                                                                       textOffset,
                                                                                                                                       value
                                                                                                                                         .getTextRange()
                                                                                                                                         .getEndOffset() -
                                                                                                                                                         textOffset) +
                                                                                                                                                                     "</code>")));
          }
        }
      }
    }
    return false;
  }


  public String getDisplayName() {
    return InspectionsBundle.message("inspection.javadoc.ref.display.name");
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return JavaDocReferenceInspection.SHORT_NAME;
  }

  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
