// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

final public class JavaFoldingUtil {
  private static final Logger LOG = Logger.getInstance(JavaFoldingUtil.class);

  private JavaFoldingUtil() { }

  @NotNull
  static String getCodeBlockPlaceholder(@Nullable PsiElement codeBlock) {
    return codeBlock instanceof PsiCodeBlock && ((PsiCodeBlock)codeBlock).isEmpty() ? "{}" : "{...}";
  }

  static boolean areOnAdjacentLines(@NotNull PsiElement e1, @NotNull PsiElement e2, @NotNull Document document) {
    return document.getLineNumber(e1.getTextRange().getEndOffset()) + 1 == document.getLineNumber(e2.getTextRange().getStartOffset());
  }


  static @Nullable TextRange importListRange(@NotNull PsiImportList list) {
    PsiImportStatementBase[] statements = list.getAllImportStatements();
    if (statements.length == 0) return null;
    final PsiElement importKeyword = statements[0].getFirstChild();
    if (importKeyword == null) return null;
    int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
    int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
    return hasErrorElementsNearby(list.getContainingFile(), startOffset, endOffset) ? null : new TextRange(startOffset, endOffset);
  }

  static @Nullable TextRange lambdaRange(@NotNull PsiLambdaExpression lambdaExpression) {
    PsiElement body = lambdaExpression.getBody();
    return body instanceof PsiCodeBlock ? body.getTextRange() : null;
  }

  static @Nullable TextRange methodRange(@NotNull PsiMethod element) {
    PsiCodeBlock body = element.getBody();
    return body == null ? null : body.getTextRange();
  }

  static @Nullable TextRange classRange(@NotNull PsiClass aClass) {
    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) return null;
    PsiElement rBrace = aClass.getRBrace();
    if (rBrace == null) return null;
    return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
  }

  static @Nullable TextRange moduleRange(@NotNull PsiJavaModule element) {
    PsiElement left = SyntaxTraverser.psiTraverser().children(element).find(e -> PsiUtil.isJavaToken(e, JavaTokenType.LBRACE));
    PsiElement right = SyntaxTraverser.psiTraverser().children(element).find(e -> PsiUtil.isJavaToken(e, JavaTokenType.RBRACE));
    return left != null && right != null ? new TextRange(left.getTextOffset(), right.getTextOffset() + 1) : null;
  }

  static @NotNull TextRange annotationRange(@NotNull PsiAnnotation annotation) {
    TextRange parameterListRange = annotation.getParameterList().getTextRange();
    int startOffset = parameterListRange.getStartOffset();
    return new TextRange(startOffset, parameterListRange.getEndOffset());
  }

  public static boolean hasErrorElementsNearby(@NotNull PsiFile file, int startOffset, int endOffset) {
    endOffset = CharArrayUtil.shiftForward(file.getViewProvider().getContents(), endOffset, " \t\n");
    for (PsiElement element : CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset)) {
      if (element instanceof PsiErrorElement) {
        return true;
      }
    }
    return false;
  }

  static @Nullable TextRange fileHeaderRange(@NotNull PsiJavaFile file) {
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    PsiElement element = first;
    while (element instanceof PsiComment) {
      if (element instanceof PsiDocComment && PsiTreeUtil.skipWhitespacesForward(element) instanceof PsiPackageStatement &&
          PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
        break;
      }
      element = element.getNextSibling();
      if (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      else {
        break;
      }
    }
    if (element == null) return null;
    PsiElement prevSibling = element.getPrevSibling();
    if (prevSibling instanceof PsiWhiteSpace) element = prevSibling;
    if (element.equals(first)) return null;
    return new UnfairTextRange(first.getTextOffset(), element.getTextOffset());
  }

  static void addAnnotationsToFold(@NotNull List<? super FoldingDescriptor> list, @Nullable PsiModifierList modifierList,
                                   @NotNull Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; ) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        PsiAnnotation annotation = (PsiAnnotation)child;
        addToFold(list, annotation, document, false, "(...)", annotationRange(annotation),
                  JavaCodeFoldingSettings.getInstance().isCollapseAnnotations());
        annotation.acceptChildren(new NestedAnnotationsVisitor(list, document));
      }
      int j;
      for (j = i + 1; j < children.length; j++) {
        PsiElement nextChild = children[j];
        if (nextChild instanceof PsiModifier || nextChild instanceof PsiAnnotation) break;
      }
      i = j;
    }
  }

  private static class NestedAnnotationsVisitor extends JavaRecursiveElementWalkingVisitor {
    @NotNull private final List<? super FoldingDescriptor> myList;
    @NotNull private final Document myDocument;

    private NestedAnnotationsVisitor(@NotNull List<? super FoldingDescriptor> list, @NotNull Document document) {
      myList = list;
      myDocument = document;
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      addToFold(myList, annotation, myDocument, false, "(...)", annotationRange(annotation),
                JavaCodeFoldingSettings.getInstance().isCollapseAnnotations());
    }
  }

  static void addAllCommentsToFold(@NotNull List<? super FoldingDescriptor> list,
                                   @NotNull PsiElement element,
                                   @NotNull Document document) {
    final Collection<PsiComment> comments = PsiTreeUtil.collectElementsOfType(element, PsiComment.class);
    final HashSet<PsiElement> processedComments = new HashSet<>();

    for (PsiComment comment : comments) {
      addCommentToFold(list, comment, document, processedComments);
    }
  }

  private static void addCommentToFold(@NotNull List<? super FoldingDescriptor> list,
                                       @NotNull PsiComment comment,
                                       @NotNull Document document,
                                       @NotNull HashSet<PsiElement> processedComments) {
    final FoldingDescriptor commentDescriptor;
    if (comment instanceof PsiDocComment && ((PsiDocComment)comment).isMarkdownComment()) {
      // FIXME: inline documentation comments aren't supported in the Commenter interface
      if (!processedComments.add(comment)) return;
      String placeholder = CommentFoldingUtil.getCommentPlaceholder(document, JavaDocElementType.DOC_COMMENT, comment.getTextRange());
      if (placeholder == null) placeholder = "/// ...";
      // Hack: Markdown comments aren't documented in the Commenter for the Java language
      // To avoid the `/** */` tokens, we remove them
      placeholder = StringUtil.trimEnd(StringUtil.trimStart(placeholder, "/**"), "*/");
      commentDescriptor =
        new FoldingDescriptor(comment.getNode(), comment.getTextRange(), null, placeholder, isCollapseCommentByDefault(comment),
                              Collections.emptySet());
    }
    else {
      commentDescriptor = CommentFoldingUtil.getCommentDescriptor(comment, document, processedComments,
                                                                  element -> CustomFoldingBuilder.isCustomRegionElement(element),
                                                                  isCollapseCommentByDefault(comment));
    }

    if (commentDescriptor != null) {
      list.add(commentDescriptor);
    }
  }

  static void addToFold(@NotNull List<? super FoldingDescriptor> list,
                                @NotNull PsiElement elementToFold,
                                @NotNull Document document,
                                boolean allowOneLiners,
                                @NotNull String placeholder,
                                @Nullable TextRange range,
                                boolean isCollapsedByDefault) {
    if (range != null) {
      PsiUtilCore.ensureValid(elementToFold);
      addFoldRegion(list, elementToFold, document, allowOneLiners, range, placeholder, isCollapsedByDefault);
    }
  }

  static void addFoldRegion(@NotNull List<? super FoldingDescriptor> list,
                                    @NotNull PsiElement elementToFold,
                                    @NotNull Document document,
                                    boolean allowOneLiners,
                                    @NotNull TextRange range, @NotNull String placeholder, boolean isCollapsedByDefault) {
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return;

    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());

    if (!allowOneLiners) {
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine >= endLine || range.getLength() <= 1) {
        return;
      }
    }
    else if (range.getLength() <= placeholder.length()) {
      return;
    }
    list.add(new FoldingDescriptor(elementToFold.getNode(), range, null, placeholder, isCollapsedByDefault, Collections.emptySet()));
  }

  /**
   * Determines whether comment should be collapsed by default.
   * If comment has unknown type then it is not collapsed.
   */
  private static boolean isCollapseCommentByDefault(@NotNull PsiComment comment) {
    final JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();

    final PsiElement parent = comment.getParent();
    if (parent instanceof PsiJavaFile) {
      if (((PsiJavaFile)parent).getName().equals(PsiPackage.PACKAGE_INFO_FILE)) {
        return false;
      }
      PsiElement firstChild = parent.getFirstChild();
      if (firstChild instanceof PsiWhiteSpace) {
        firstChild = firstChild.getNextSibling();
      }
      if (comment.equals(firstChild)) {
        return settings.isCollapseFileHeader();
      }
    }

    if (comment instanceof PsiDocComment) return settings.isCollapseJavadocs();

    final IElementType commentType = comment.getTokenType();

    if (commentType == JavaTokenType.END_OF_LINE_COMMENT) return settings.isCollapseEndOfLineComments();
    if (commentType == JavaTokenType.C_STYLE_COMMENT) return settings.isCollapseMultilineComments();

    return false;
  }

  static void addFoldsForImports(@NotNull List<? super FoldingDescriptor> list, @NotNull PsiJavaFile file) {
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      PsiImportStatementBase[] statements = importList.getAllImportStatements();
      if (statements.length > 1) {
        final TextRange rangeToFold = importListRange(importList);
        if (rangeToFold != null && rangeToFold.getLength() > 1) {
          FoldingDescriptor descriptor = new FoldingDescriptor(importList.getNode(), rangeToFold, null, "...",
                                                               JavaCodeFoldingSettings.getInstance().isCollapseImports(),
                                                               Collections.emptySet());
          // imports are often added/removed automatically, so we enable auto-update of folded region for foldings even if it's collapsed
          descriptor.setCanBeRemovedWhenCollapsed(true);
          list.add(descriptor);
        }
      }
    }
  }

  static void addFoldsForFileHeader(@NotNull List<? super FoldingDescriptor> list,
                                            @NotNull PsiJavaFile file,
                                            @NotNull Document document) {
    TextRange range = fileHeaderRange(file);
    if (range != null &&
        range.getLength() > 1 &&
        document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
      PsiElement anchorElementToUse = file;
      PsiElement candidate = file.getFirstChild();

      // We experienced the following problem situation:
      //     1. There is a collapsed class-level javadoc;
      //     2. User starts typing at class definition line (e.g. we had definition like 'public class Test' and user starts
      //        typing 'abstract' between 'public' and 'class');
      //     3. Collapsed class-level javadoc automatically expanded. That happened because PSI structure became invalid (because
      //        class definition line at start looks like 'public class Test');
      // So, our point is to preserve fold descriptor referencing javadoc PSI element.
      if (candidate != null && candidate.getTextRange().equals(range)) {
        ASTNode node = candidate.getNode();
        if (node != null && JavaDocElementType.DOC_COMMENT_TOKENS.contains(node.getElementType())) {
          anchorElementToUse = candidate;
        }
      }
      list.add(new FoldingDescriptor(anchorElementToUse.getNode(), range, null, "/.../",
                                     JavaCodeFoldingSettings.getInstance().isCollapseFileHeader(), Collections.emptySet()));
    }
  }

  static void addFoldsForModule(@NotNull List<? super FoldingDescriptor> list,
                                        @NotNull PsiJavaModule module,
                                        @NotNull Document document) {
    addToFold(list, module, document, true, getCodeBlockPlaceholder(null), moduleRange(module), false);
    addAnnotationsToFold(list, module.getModifierList(), document);
  }
}
