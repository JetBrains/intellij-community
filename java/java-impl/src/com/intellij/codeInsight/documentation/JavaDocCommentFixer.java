/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
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

  @NotNull private static final Comparator<PsiElement> COMPARATOR =
    (e1, e2) -> e2.getTextRange().getEndOffset() - e1.getTextRange().getEndOffset();

  @NotNull private static final String PARAM_TAG_NAME = "param";

  @Override
  public void fixComment(@NotNull Project project, @NotNull Editor editor, @NotNull PsiComment comment) {
    if (!(comment instanceof PsiDocComment)) {
      return;
    }

    PsiDocComment docComment = (PsiDocComment)comment;
    PsiDocCommentOwner owner = docComment.getOwner();
    if (owner == null) {
      return;
    }
    
    PsiFile file = comment.getContainingFile();
    if (file == null) {
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
    ensureContentOrdered(docComment, editor.getDocument());
    locateCaret(docComment, editor, file);
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
    
    localInspection.setIgnoreEmptyDescriptions(true);

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

  /**
   * This fixer is based on existing javadoc inspections - there are two of them. One detects invalid references (to unexisted
   * method parameter or non-declared checked exception). Another one handles all other cases (parameter documentation is missing;
   * parameter doesn't have a description etc). This method handles result of the second exception
   * 
   * @param problems  detected problems
   * @param comment   target comment to fix
   * @param document  target document which contains text of the commen being fixed
   * @param project   current project
   */
  @SuppressWarnings("unchecked")
  private static void fixCommonProblems(@NotNull ProblemDescriptor[] problems,
                                        @NotNull PsiComment comment,
                                        @NotNull final Document document,
                                        @NotNull Project project)
  {
    List<PsiElement> toRemove = new ArrayList<>();
    for (ProblemDescriptor problem : problems) {
      PsiElement element = problem.getPsiElement();
      if (element == null) {
        continue;
      }
      if ((!(element instanceof PsiDocToken) || !JavaDocTokenType.DOC_COMMENT_START.equals(((PsiDocToken)element).getTokenType())) &&
          comment.getTextRange().contains(element.getTextRange())) {
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

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
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
    psiDocumentManager.commitDocument(document);
  }

  private static void ensureContentOrdered(@NotNull PsiDocComment comment, @NotNull Document document) {
    //region Parse existing doc comment parameters.
    List<String> current = new ArrayList<>();
    Map<String, Pair<TextRange, String>> tagInfoByName = new HashMap<>();
    for (PsiDocTag tag : comment.getTags()) {
      if (!PARAM_TAG_NAME.equals(tag.getName())) {
        continue;
      }
      PsiDocTagValue valueElement = tag.getValueElement();
      if (valueElement == null) {
        continue;
      }
      String paramName = valueElement.getText();
      if (paramName != null) {
        current.add(paramName);
        tagInfoByName.put(paramName, parseTagValue(tag, document));
      }
    }
    //endregion


    //region Calculate desired parameters order
    List<String> ordered = new ArrayList<>();
    PsiDocCommentOwner owner = comment.getOwner();
    if ((owner instanceof PsiMethod)) {
      PsiParameter[] parameters = ((PsiMethod)owner).getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        ordered.add(parameter.getName());
      }
    }
    if (owner instanceof PsiTypeParameterListOwner) {
      PsiTypeParameter[] typeParameters = ((PsiTypeParameterListOwner)owner).getTypeParameters();
      for (PsiTypeParameter parameter : typeParameters) {
        ordered.add(String.format("<%s>", parameter.getName()));
      }
    }
    //endregion

    //region Fix order if necessary.
    if (current.size() != ordered.size()) {
      // Something is wrong, stop the processing.
      return;
    }

    boolean changed = false;
    for (int i = current.size() - 1; i >= 0; i--) {
      String newTag = ordered.get(i);
      String oldTag = current.get(i);
      if (newTag.equals(oldTag)) {
        continue;
      }
      TextRange range = tagInfoByName.get(oldTag).first;
      document.replaceString(range.getStartOffset(), range.getEndOffset(), tagInfoByName.get(newTag).second);
      changed = true;
    }

    if (changed) {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(comment.getProject());
      manager.commitDocument(document);
    }
    //endregion
  }

  @NotNull
  private static Pair<TextRange, String> parseTagValue(@NotNull PsiDocTag tag, @NotNull Document document) {
    PsiDocTagValue valueElement = tag.getValueElement();
    assert valueElement != null;
    
    int startOffset = valueElement.getTextRange().getStartOffset();
    int endOffset = tag.getTextRange().getEndOffset();
    // Javadoc PSI is rather weird...
    CharSequence text = document.getCharsSequence();
    int i = CharArrayUtil.shiftBackward(text, endOffset - 1, " \t*");
    if (i > 0 && text.charAt(i) == '\n') {
      endOffset = i;
    }
    
    return Pair.create(TextRange.create(startOffset, endOffset), text.subSequence(startOffset, endOffset).toString());
  }
  
  private static void locateCaret(@NotNull PsiDocComment comment, @NotNull Editor editor, @NotNull PsiFile file) {
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
      JavadocNavigationDelegate.navigateToLineEnd(editor, file);
    }
  }
}
