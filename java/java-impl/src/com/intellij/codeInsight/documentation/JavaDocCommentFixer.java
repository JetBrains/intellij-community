/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.javadoc.JavadocNavigationDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 9/20/12 8:44 PM
 */
public class JavaDocCommentFixer implements DocCommentFixer {

  @NotNull private static final String PARAM_TAG = "@param";

  /**
   * Lists tags eligible for moving caret to after javadoc fixing. The main idea is that we want to locate caret at the 
   * incomplete tag description after fixing the doc comment.
   * <p/>
   * Example:
   * <pre>
   *   class Test {
   *     &#47;**
   *      * Method description
   *      *
   *      * @param i    'i' argument
   *      * @param j    [we want to move the caret here because j's description is missing]
   *      *&#47;
   *     void test(int i, int j) {
   *     }
   *   }
   * </pre>
   */
  @NotNull private static final Set<String> CARET_ANCHOR_TAGS = ContainerUtilRt.newHashSet(PARAM_TAG, "@throws", "@return");

  @NotNull private static final List<String> TAGS_ORDER = new ArrayList<String>();
  static {
    String tags = System.getProperty("java.doc.comment.fix.tags.order");
    if (tags == null) {
      tags = "@param:@return:@throws";
    }

    for (String s : tags.split(":")) {
      String tagName = s.trim();
      if (!tagName.isEmpty()) {
        TAGS_ORDER.add("@" + tagName);
      }
    }
  }

  private static final Comparator<PsiElement> COMPARATOR = new Comparator<PsiElement>() {
    @Override
    public int compare(PsiElement e1, PsiElement e2) {
      return e2.getTextRange().getEndOffset() - e1.getTextRange().getEndOffset();
    }
  };

  @Override
  public void fixComment(@NotNull Project project, @NotNull Editor editor, @NotNull PsiComment comment) {
    if (!(comment instanceof PsiDocComment)) {
      return;
    }

    PsiDocCommentOwner owner = ((PsiDocComment)comment).getOwner();
    if (owner == null) {
      return;
    }

    JavaDocReferenceInspection referenceInspection = new JavaDocReferenceInspection();
    JavaDocLocalInspection localInspection = getDocLocalInspection();

    InspectionManager inspectionManager = InspectionManager.getInstance(project);
    ProblemDescriptor[] referenceProblems = null;
    ProblemDescriptor[] otherProblems = null;
    if (owner instanceof PsiClass) {
      referenceProblems = referenceInspection.checkClass(((PsiClass)owner), inspectionManager, false);
      otherProblems = localInspection.checkClass(((PsiClass)owner), inspectionManager, false);
    }
    else if (owner instanceof PsiField) {
      referenceProblems = referenceInspection.checkField(((PsiField)owner), inspectionManager, false);
      otherProblems = localInspection.checkField(((PsiField)owner), inspectionManager, false);
    }
    else if (owner instanceof PsiMethod) {
      referenceProblems = referenceInspection.checkMethod((PsiMethod)owner, inspectionManager, false);
      otherProblems = localInspection.checkMethod((PsiMethod)owner, inspectionManager, false);
    }

    if (referenceProblems != null) {
      fixReferenceProblems(referenceProblems, project);
    }
    if (otherProblems != null) {
      fixCommonProblems(otherProblems, comment, editor.getDocument(), project);
    }
    
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    locateCaret((PsiDocComment)comment, editor);
  }

  @NotNull
  private static JavaDocLocalInspection getDocLocalInspection() {
    JavaDocLocalInspection localInspection = new JavaDocLocalInspection();

    //region visibility
    localInspection.TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = PsiModifier.PRIVATE;
    localInspection.INNER_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = PsiModifier.PRIVATE;
    localInspection.FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = PsiModifier.PRIVATE;
    localInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = PsiModifier.PRIVATE;
    //endregion
    
    localInspection.IGNORE_EMPTY_DESCRIPTIONS = true;

    //region class type arguments
    if (!localInspection.TOP_LEVEL_CLASS_OPTIONS.REQUIRED_TAGS.contains(PARAM_TAG)) {
      localInspection.TOP_LEVEL_CLASS_OPTIONS.REQUIRED_TAGS += PARAM_TAG;
    }
    if (!localInspection.INNER_CLASS_OPTIONS.REQUIRED_TAGS.contains(PARAM_TAG)) {
      localInspection.INNER_CLASS_OPTIONS.REQUIRED_TAGS += PARAM_TAG;
    }
    //endregion
    
    return localInspection;
  }

  @SuppressWarnings("unchecked")
  private static void fixReferenceProblems(@NotNull ProblemDescriptor[] problems, @NotNull Project project) {
    for (ProblemDescriptor problem : problems) {
      QuickFix[] fixes = problem.getFixes();
      if (fixes != null) {
        fixes[0].applyFix(project, problem);
      }
    }
  }

  // TODO den add doc
  @SuppressWarnings("unchecked")
  private static void fixCommonProblems(@NotNull ProblemDescriptor[] problems,
                                        @NotNull PsiComment comment,
                                        @NotNull final Document document,
                                        @NotNull Project project)
  {
    List<PsiElement> toRemove = new ArrayList<PsiElement>();
    for (ProblemDescriptor problem : problems) {
      PsiElement element = problem.getPsiElement();
      if (element == null) {
        continue;
      }
      if (comment.getTextRange().contains(element.getTextRange())) {
        // Unnecessary element like '@return' at the void method's javadoc.
        for (PsiElement e = element; e != null; e = e.getParent()) {
          if (e instanceof PsiDocTag) {
            toRemove.add(e);
            break;
          }
        }
      }
      else {
        // Problems like 'missing @param'.
        QuickFix[] fixes = problem.getFixes();
        if (fixes != null && fixes.length > 0) {
          fixes[0].applyFix(project, problem);
        }
      }
    }
    
    if (toRemove.isEmpty()) {
      return;
    }
    if (toRemove.size() > 1) {
      Collections.sort(toRemove, COMPARATOR);
    }
    
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    CharSequence text = document.getCharsSequence();
    for (PsiElement element : toRemove) {
      int startOffset = element.getTextRange().getStartOffset();
      int startLine = document.getLineNumber(startOffset);
      int i = CharArrayUtil.shiftBackward(text, startOffset - 1, " \t");
      if (i >= 0) {
        char c = text.charAt(i);
        if (c == '*') {
          i = CharArrayUtil.shiftBackward(text, i - 1, " \t");
        }
      }
      if (i >= 0 && text.charAt(i) == '\n') {
        startOffset = Math.max(i, document.getLineStartOffset(startLine) - 1);
      }

      int endOffset = element.getTextRange().getEndOffset();
      // Javadoc PSI is awkward, it includes next line text before the next tag. That's why we need to strip it.
      i = CharArrayUtil.shiftBackward(text, endOffset - 1, " \t*");
      if (i > 0 && text.charAt(i) == '\n') {
        endOffset = i;
      }
      document.deleteString(startOffset, endOffset);
    }
  }

  private static void locateCaret(@NotNull PsiDocComment comment, @NotNull Editor editor) {
    Document document = editor.getDocument();
    int lineToNavigate = -1;
    for (PsiDocTag tag : comment.getTags()) {
      PsiElement nameElement = tag.getNameElement();
      if (nameElement == null || !CARET_ANCHOR_TAGS.contains(nameElement.getText())) {
        continue;
      }
      boolean good = false;
      PsiElement[] dataElements = tag.getDataElements();
      if (dataElements != null) {
        PsiDocTagValue valueElement = tag.getValueElement();
        for (PsiElement element : dataElements) {
          if (element == valueElement) {
            continue;
          }
          if (!StringUtil.isEmptyOrSpaces(element.getText())) {
            good = true;
            break;
          }
        }
      }
      if (!good) {
        int offset = tag.getTextRange().getEndOffset();
        CharSequence text = document.getCharsSequence();
        int i = CharArrayUtil.shiftBackward(text, offset - 1, " \t*");
        if (i > 0 && text.charAt(i) == '\n') {
          offset = i - 1;
        }
        lineToNavigate = document.getLineNumber(offset);
        break;
      }
    }

    if (lineToNavigate >= 0) {
      editor.getCaretModel().moveToOffset(document.getLineEndOffset(lineToNavigate));
      JavadocNavigationDelegate.navigateToLineEnd(editor, comment.getContainingFile());
    }
  }
}
