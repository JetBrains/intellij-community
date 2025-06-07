// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection;
import com.intellij.javadoc.JavadocNavigationDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class JavaDocCommentFixer implements DocCommentFixer {
  private static final String PARAM_TAG = "@param";
  private static final String PARAM_TAG_NAME = "param";

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
  private static final Set<String> CARET_ANCHOR_TAGS = ContainerUtil.newHashSet(PARAM_TAG, "@throws", "@return");

  private static final Comparator<TextRange> COMPARATOR = (e1, e2) -> e2.getEndOffset() - e1.getEndOffset();

  @Override
  public void fixComment(@NotNull Project project, @NotNull Editor editor, @NotNull PsiComment comment) {
    if (!(comment instanceof PsiDocComment docComment)) {
      return;
    }

    PsiJavaDocumentedElement owner = docComment.getOwner();
    if (owner == null) return;
    PsiFile file = owner.getContainingFile();
    if (file == null) return;

    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> referenceProblems =
      InspectionEngine.inspectElements(Collections.singletonList(new LocalInspectionToolWrapper(new JavaDocReferenceInspection())), file,
                                       file.getTextRange(),
                                       true, false, new DaemonProgressIndicator(), Collections.singletonList(owner), PairProcessor.alwaysTrue());

    List<LocalInspectionToolWrapper> toolWrappers = List.of(
      new LocalInspectionToolWrapper(getMissingJavadocInspection()), new LocalInspectionToolWrapper(getJavadocDeclarationInspection())
    );
    Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> commonProblems =
      InspectionEngine.inspectElements(toolWrappers, file, file.getTextRange(), true, true,
                                       new DaemonProgressIndicator(), Collections.singletonList(owner), PairProcessor.alwaysTrue());

    if (!referenceProblems.isEmpty()) {
      fixReferenceProblems(ContainerUtil.flatten(referenceProblems.values()), project);
    }
    Document document = file.getFileDocument();
    if (!commonProblems.isEmpty()) {
      fixCommonProblems(ContainerUtil.flatten(commonProblems.values()), comment, document, project);
    }

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    ensureContentOrdered(docComment, document);
    locateCaret(docComment, editor, file);
  }

  private static @NotNull MissingJavadocInspection getMissingJavadocInspection() {
    MissingJavadocInspection localInspection = new MissingJavadocInspection();

    //region visibility
    localInspection.TOP_LEVEL_CLASS_SETTINGS.MINIMAL_VISIBILITY = PsiModifier.PRIVATE;
    localInspection.INNER_CLASS_SETTINGS.MINIMAL_VISIBILITY = PsiModifier.PRIVATE;
    localInspection.FIELD_SETTINGS.MINIMAL_VISIBILITY = PsiModifier.PRIVATE;
    localInspection.METHOD_SETTINGS.MINIMAL_VISIBILITY = PsiModifier.PRIVATE;
    //endregion

    //region class type arguments
    if (!localInspection.TOP_LEVEL_CLASS_SETTINGS.isTagRequired(PARAM_TAG)) {
      localInspection.TOP_LEVEL_CLASS_SETTINGS.setTagRequired(PARAM_TAG, true);
    }
    if (!localInspection.INNER_CLASS_SETTINGS.isTagRequired(PARAM_TAG)) {
      localInspection.INNER_CLASS_SETTINGS.setTagRequired(PARAM_TAG, true);
    }
    //endregion

    return localInspection;
  }

  private static @NotNull JavadocDeclarationInspection getJavadocDeclarationInspection() {
    JavadocDeclarationInspection localInspection = new JavadocDeclarationInspection();
    localInspection.setIgnoreEmptyDescriptions(true);
    return localInspection;
  }

  @SuppressWarnings("unchecked")
  private static void fixReferenceProblems(@NotNull List<? extends ProblemDescriptor> problems, @NotNull Project project) {
    for (ProblemDescriptor problem : problems) {
      QuickFix[] fixes = problem.getFixes();
      if (fixes != null) {
        fixes[0].applyFix(project, problem);
      }
    }
  }

  /**
   * This fixer is based on existing javadoc inspections - there are two of them. One detects invalid references (to nonexistent
   * method parameter or non-declared checked exception). Another one handles all other cases (parameter documentation is missing;
   * parameter doesn't have a description etc). This method handles result of the second exception
   *
   * @param problems detected problems
   * @param comment  target comment to fix
   * @param document target document which contains text of the comment being fixed
   * @param project  current project
   */
  @SuppressWarnings("unchecked")
  private static void fixCommonProblems(@NotNull List<? extends ProblemDescriptor> problems,
                                        @NotNull PsiComment comment,
                                        final @NotNull Document document,
                                        @NotNull Project project) {
    List<RangeMarker> toRemove = new ArrayList<>();
    List<ProblemDescriptor> problemsToApply = new ArrayList<>();
    for (ProblemDescriptor problem : problems) {
      PsiElement element = problem.getPsiElement();
      if (element == null) {
        continue;
      }
      if (!PsiDocToken.isDocToken(element, JavaDocTokenType.DOC_COMMENT_START) && comment.getTextRange().contains(element.getTextRange())) {
        // Unnecessary element like '@return' at the void method's javadoc.
        for (PsiElement e = element; e != null; e = e.getParent()) {
          if (e instanceof PsiDocTag) {
            toRemove.add(document.createRangeMarker(e.getTextRange()));
            break;
          }
        }
      }
      else {
        problemsToApply.add(problem);
      }
    }

    for (ProblemDescriptor problem : problemsToApply) {
      // Problems like 'missing @param'.
      QuickFix[] fixes = problem.getFixes();
      if (fixes != null && fixes.length > 0) {
        fixes[0].applyFix(project, problem);
      }
    }

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    if (toRemove.isEmpty()) {
      psiDocumentManager.commitDocument(document);
      return;
    }

    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    CharSequence text = document.getCharsSequence();
    List<TextRange> toDelete = new ArrayList<>();
    for (RangeMarker rangeMarker : toRemove) {
      TextRange range = rangeMarker.getTextRange();
      int startOffset = range.getStartOffset();
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

      int endOffset = range.getEndOffset();
      // Javadoc PSI is awkward, it includes next line text before the next tag. That's why we need to strip it.
      i = CharArrayUtil.shiftBackward(text, endOffset - 1, " \t*");
      if (i > 0 && text.charAt(i) == '\n') {
        endOffset = i;
      }
      toDelete.add(new TextRange(startOffset, endOffset));
      rangeMarker.dispose();
    }

    toDelete.sort(COMPARATOR);
    for (TextRange range : toDelete) {
      document.deleteString(range.getStartOffset(), range.getEndOffset());
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
    PsiJavaDocumentedElement owner = comment.getOwner();
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

  private static @NotNull Pair<TextRange, String> parseTagValue(@NotNull PsiDocTag tag, @NotNull Document document) {
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
    Document document = file.getFileDocument();
    int lineToNavigate = -1;
    for (PsiDocTag tag : comment.getTags()) {
      PsiElement nameElement = tag.getNameElement();
      if (nameElement == null || !CARET_ANCHOR_TAGS.contains(nameElement.getText())) {
        continue;
      }
      boolean good = false;
      PsiElement[] dataElements = tag.getDataElements();
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