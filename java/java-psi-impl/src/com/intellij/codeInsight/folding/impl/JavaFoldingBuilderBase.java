/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class JavaFoldingBuilderBase extends CustomFoldingBuilder implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.JavaFoldingBuilder");

  private static String getCodeBlockPlaceholder(PsiElement codeBlock) {
    return codeBlock instanceof PsiCodeBlock && ((PsiCodeBlock)codeBlock).isEmpty() ? "{}" : "{...}";
  }

  private static boolean areOnAdjacentLines(@NotNull PsiElement e1, @NotNull PsiElement e2, @NotNull Document document) {
    return document.getLineNumber(e1.getTextRange().getEndOffset()) + 1 == document.getLineNumber(e2.getTextRange().getStartOffset());
  }

  private static boolean isSimplePropertyAccessor(@NotNull PsiMethod method) {
    if (DumbService.isDumb(method.getProject())) return false;

    PsiCodeBlock body = method.getBody();
    if (body == null || body.getLBrace() == null || body.getRBrace() == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) return false;

    PsiStatement statement = statements[0];
    if (PropertyUtilBase.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        return ((PsiReturnStatement)statement).getReturnValue() instanceof PsiReferenceExpression;
      }
      return false;
    }

    // builder-style setter?
    if (statements.length > 1 && !(statements[1] instanceof PsiReturnStatement)) return false;

    // any setter?
    if (statement instanceof PsiExpressionStatement) {
      PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
      if (expr instanceof PsiAssignmentExpression) {
        PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
        PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
        return lhs instanceof PsiReferenceExpression &&
               rhs instanceof PsiReferenceExpression &&
               !((PsiReferenceExpression)rhs).isQualified() &&
               PropertyUtilBase.isSimplePropertySetter(method); // last check because it can perform long return type resolve
      }
    }
    return false;
  }

  @Nullable
  private static TextRange importListRange(@NotNull PsiImportList list) {
    PsiImportStatementBase[] statements = list.getAllImportStatements();
    if (statements.length == 0) return null;
    final PsiElement importKeyword = statements[0].getFirstChild();
    if (importKeyword == null) return null;
    int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
    int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
    return hasErrorElementsNearby(list.getContainingFile(), startOffset, endOffset) ? null : new TextRange(startOffset, endOffset);
  }

  @Nullable
  private static TextRange lambdaRange(@NotNull PsiLambdaExpression lambdaExpression) {
    PsiElement body = lambdaExpression.getBody();
    return body instanceof PsiCodeBlock ? body.getTextRange() : null;
  }

  @Nullable
  private static TextRange methodRange(@NotNull PsiMethod element) {
    PsiCodeBlock body = element.getBody();
    return body == null ? null : body.getTextRange();
  }

  @Nullable
  private static TextRange classRange(@NotNull PsiClass aClass) {
    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) return null;
    PsiElement rBrace = aClass.getRBrace();
    if (rBrace == null) return null;
    return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
  }

  @Nullable
  private static TextRange moduleRange(@NotNull PsiJavaModule element) {
    PsiElement left = SyntaxTraverser.psiTraverser().children(element).find(e -> PsiUtil.isJavaToken(e, JavaTokenType.LBRACE));
    PsiElement right = SyntaxTraverser.psiTraverser().children(element).find(e -> PsiUtil.isJavaToken(e, JavaTokenType.RBRACE));
    return left != null && right != null ? new TextRange(left.getTextOffset(), right.getTextOffset() + 1) : null;
  }

  @NotNull
  private static TextRange annotationRange(@NotNull PsiAnnotation annotation) {
    PsiElement element = annotation;
    int startOffset = element.getTextRange().getStartOffset();
    PsiElement last = element;
    while (element instanceof PsiAnnotation) {
      last = element;
      element = PsiTreeUtil.skipWhitespacesAndCommentsForward(element);
    }

    return new TextRange(startOffset, last.getTextRange().getEndOffset());
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

  @Nullable
  private static TextRange fileHeaderRange(@NotNull PsiJavaFile file) {
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    PsiElement element = first;
    while (element instanceof PsiComment) {
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

  private static void addAnnotationsToFold(@NotNull List<? super FoldingDescriptor> list, @Nullable PsiModifierList modifierList,
                                           @NotNull Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        PsiAnnotation annotation = (PsiAnnotation)child;
        addToFold(list, annotation, document, false, "@{...}", annotationRange(annotation), JavaCodeFoldingSettings.getInstance().isCollapseAnnotations());
        int j;
        for (j = i + 1; j < children.length; j++) {
          PsiElement nextChild = children[j];
          if (nextChild instanceof PsiModifier) break;
        }

        //noinspection AssignmentToForLoopParameter
        i = j;
      }
    }
  }

  /**
   * We want to allow to fold subsequent single line comments like
   * <pre>
   *     // this is comment line 1
   *     // this is comment line 2
   * </pre>
   *
   * @param list        fold descriptors holder to store newly created descriptor (if any)
   * @param comment             comment to check
   * @param processedComments   set that contains already processed elements. It is necessary because we process all elements of
*                            the PSI tree, hence, this method may be called for both comments from the example above. However,
*                            we want to create fold region during the first comment processing, put second comment to it and
*                            skip processing when current method is called for the second element
   */
  private static void addFoldsForComment(@NotNull List<? super FoldingDescriptor> list,
                                         @NotNull PsiComment comment,
                                         @NotNull Set<? super PsiElement> processedComments) {
    if (processedComments.contains(comment) || comment.getTokenType() != JavaTokenType.END_OF_LINE_COMMENT
        || isCustomRegionElement(comment)) {
      return;
    }
    processedComments.add(comment);

    PsiElement end = null;
    for (PsiElement current = comment.getNextSibling(); current != null; current = current.getNextSibling()) {
      ASTNode node = current.getNode();
      if (node == null) {
        break;
      }
      IElementType elementType = node.getElementType();
      if (elementType == JavaTokenType.END_OF_LINE_COMMENT && !isCustomRegionElement(current) && !processedComments.contains(current)) {
        end = current;
        // We don't want to process, say, the second comment in case of three subsequent comments when it's being examined
        // during all elements traversal. I.e. we expect to start from the first comment and grab as many subsequent
        // comments as possible during the single iteration.
        processedComments.add(current);
        continue;
      }
      if (elementType == TokenType.WHITE_SPACE) {
        continue;
      }
      break;
    }

    if (end != null) {
      list.add(new NamedFoldingDescriptor(comment.getNode(),
                                          new TextRange(comment.getTextRange().getStartOffset(), end.getTextRange().getEndOffset()), null,
                                          "//...", JavaCodeFoldingSettings.getInstance().isCollapseEndOfLineComments(), Collections.emptySet()));
    }
  }

  private static void addMethodGenericParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                                        @NotNull PsiMethodCallExpression expression,
                                                        @NotNull Document document,
                                                        boolean quick) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiReferenceParameterList parameterList = methodExpression.getParameterList();
    if (parameterList == null || parameterList.getTextLength() <= 5) {
      return;
    }

    PsiMethodCallExpression element = expression;
    while (true) {
      if (!quick && !resolvesCorrectly(element.getMethodExpression())) return;
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionList) || !(parent.getParent() instanceof PsiMethodCallExpression)) break;
      element = (PsiMethodCallExpression)parent.getParent();
    }

    addTypeParametersFolding(list, document, parameterList, 3, quick);
  }

  private static boolean resolvesCorrectly(@NotNull PsiReferenceExpression expression) {
    for (final JavaResolveResult result : expression.multiResolve(true)) {
  if (!result.isValidResult()) {
    return false;
  }
}
    return true;
  }

  private static void addGenericParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                                  @NotNull PsiNewExpression expression,
                                                  @NotNull Document document,
                                                  boolean quick) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiVariable)) {
      return;
    }

    final PsiType declType = ((PsiVariable)parent).getType();
    if (!(declType instanceof PsiClassReferenceType)) {
      return;
    }

    final PsiType[] parameters = ((PsiClassType)declType).getParameters();
    if (parameters.length == 0) {
      return;
    }

   PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        classReference = anonymousClass.getBaseClassReference();

        if (quick || ClosureFolding.seemsLikeLambda(anonymousClass.getSuperClass(), anonymousClass)) {
          return;
        }
      }
    }

    if (classReference != null) {
      final PsiReferenceParameterList parameterList = classReference.getParameterList();
      if (parameterList != null) {
        if (quick) {
          final PsiJavaCodeReferenceElement declReference = ((PsiClassReferenceType)declType).getReference();
          final PsiReferenceParameterList declList = declReference.getParameterList();
          if (declList == null || !parameterList.getText().equals(declList.getText())) {
            return;
          }
        }
        else if (!Arrays.equals(parameterList.getTypeArguments(), parameters)) {
          return;
        }

        addTypeParametersFolding(list, document, parameterList, 5, quick);
      }
    }
  }

  private static void addTypeParametersFolding(@NotNull List<? super FoldingDescriptor> list,
                                               @NotNull Document document,
                                               @NotNull PsiReferenceParameterList parameterList,
                                               int ifLongerThan,
                                               boolean quick) {
    if (!quick) {
      for (final PsiType type : parameterList.getTypeArguments()) {
        if (!type.isValid()) {
          return;
        }
        if (type instanceof PsiClassType || type instanceof PsiArrayType) {
          if (PsiUtil.resolveClassInType(type) == null) {
            return;
          }
        }
      }
    }

    final String text = parameterList.getText();
    if (text.startsWith("<") && text.endsWith(">") && text.length() > ifLongerThan) {
      final TextRange range = parameterList.getTextRange();
      addFoldRegion(list, parameterList, document, true, range, "<~>", JavaCodeFoldingSettings.getInstance().isCollapseConstructorGenericParameters());
    }
  }

  protected abstract boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression);

  private static void addToFold(@NotNull List<? super FoldingDescriptor> list,
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

  private static void addFoldRegion(@NotNull List<? super FoldingDescriptor> list,
                                    @NotNull PsiElement elementToFold,
                                    @NotNull Document document,
                                    boolean allowOneLiners,
                                    @NotNull TextRange range, @NotNull String placeholder, boolean isCollapsedByDefault) {
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return;

    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());
    // PSI element text ranges may be invalid because of reparse exception (see, for example, IDEA-10617)
    if (range.getStartOffset() < 0 || range.getEndOffset() > fileRange.getEndOffset()) {
      return;
    }

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
    list.add(new NamedFoldingDescriptor(elementToFold.getNode(), range, null, placeholder, isCollapsedByDefault, Collections.emptySet()));
  }

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    if (!(root instanceof PsiJavaFile)) return;
    PsiJavaFile file = (PsiJavaFile) root;

    addFoldsForImports(descriptors, file);

    PsiJavaModule module = file.getModuleDeclaration();
    if (module != null) {
      addFoldsForModule(descriptors, module, document);
    }

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.checkCanceled();
      ProgressIndicatorProvider.checkCanceled();
      addFoldsForClass(descriptors, aClass, document, true, quick);
    }

    addFoldsForFileHeader(descriptors, file, document);
  }

  private static void addFoldsForImports(@NotNull List<? super FoldingDescriptor> list, @NotNull PsiJavaFile file) {
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      PsiImportStatementBase[] statements = importList.getAllImportStatements();
      if (statements.length > 1) {
        final TextRange rangeToFold = importListRange(importList);
        if (rangeToFold != null && rangeToFold.getLength() > 1) {
          FoldingDescriptor descriptor = new NamedFoldingDescriptor(importList.getNode(), rangeToFold, null, "...",
                                                                    JavaCodeFoldingSettings.getInstance().isCollapseImports(), Collections.emptySet());
          // imports are often added/removed automatically, so we enable auto-update of folded region for foldings even if it's collapsed
          descriptor.setCanBeRemovedWhenCollapsed(true);
          list.add(descriptor);
        }
      }
    }
  }

  private static void addFoldsForFileHeader(@NotNull List<? super FoldingDescriptor> list,
                                            @NotNull PsiJavaFile file,
                                            @NotNull Document document) {
    TextRange range = fileHeaderRange(file);
    if (range != null && range.getLength() > 1 && document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
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
        if (node != null && node.getElementType() == JavaDocElementType.DOC_COMMENT) {
          anchorElementToUse = candidate;
        }
      }
      list.add(new NamedFoldingDescriptor(anchorElementToUse.getNode(), range, null, "/.../",
                                          JavaCodeFoldingSettings.getInstance().isCollapseFileHeader(), Collections.emptySet()));
    }
  }

  private static void addFoldsForModule(@NotNull List<? super FoldingDescriptor> list,
                                        @NotNull PsiJavaModule module,
                                        @NotNull Document document) {
    addToFold(list, module, document, true, getCodeBlockPlaceholder(null), moduleRange(module), false);
    addDocCommentToFold(list, document, module);
    addAnnotationsToFold(list, module.getModifierList(), document);
  }

  private void addFoldsForClass(@NotNull List<? super FoldingDescriptor> list,
                                @NotNull PsiClass aClass,
                                @NotNull Document document,
                                boolean foldJavaDocs,
                                boolean quick) {
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiJavaFile) || ((PsiJavaFile)parent).getClasses().length > 1) {
      addToFold(list, aClass, document, true, getCodeBlockPlaceholder(null), classRange(aClass), !(parent instanceof PsiFile) && JavaCodeFoldingSettings.getInstance().isCollapseInnerClasses());
    }

    if (foldJavaDocs) {
      addDocCommentToFold(list, document, aClass);
    }

    addAnnotationsToFold(list, aClass.getModifierList(), document);

    Set<PsiElement> processedComments = new HashSet<>();
    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      ProgressIndicatorProvider.checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;

        addFoldsForMethod(list, method, document, foldJavaDocs, quick, processedComments);
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;

        if (foldJavaDocs) {
          addDocCommentToFold(list, document, field);
        }

        addAnnotationsToFold(list, field.getModifierList(), document);

        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          addCodeBlockFolds(list, initializer, processedComments, document, quick);
        }
        else if (field instanceof PsiEnumConstant) {
          addCodeBlockFolds(list, field, processedComments, document, quick);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        addToFold(list, child, document, true, getCodeBlockPlaceholder(initializer.getBody()), initializer.getBody().getTextRange(),
                  JavaCodeFoldingSettings.getInstance().isCollapseMethods());
        addCodeBlockFolds(list, child, processedComments, document, quick);
      }
      else if (child instanceof PsiClass) {
        addFoldsForClass(list, (PsiClass)child, document, true, quick);
      }
      else if (child instanceof PsiComment) {
        addFoldsForComment(list, (PsiComment)child, processedComments);
      }
    }
  }

  private void addFoldsForMethod(@NotNull List<? super FoldingDescriptor> list,
                                 @NotNull PsiMethod method,
                                 @NotNull Document document,
                                 boolean foldJavaDocs,
                                 boolean quick,
                                 @NotNull Set<PsiElement> processedComments) {
    boolean oneLiner = addOneLineMethodFolding(list, method);
    if (!oneLiner) {
      addToFold(list, method, document, true, getCodeBlockPlaceholder(method.getBody()), methodRange(method), isCollapseMethodByDefault(method));
    }

    addAnnotationsToFold(list, method.getModifierList(), document);

    if (foldJavaDocs) {
      addDocCommentToFold(list, document, method);
    }

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      addAnnotationsToFold(list, parameter.getModifierList(), document);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null && !oneLiner) {
      addCodeBlockFolds(list, body, processedComments, document, quick);
    }
  }

  private static void addDocCommentToFold(@NotNull List<? super FoldingDescriptor> list,
                                          @NotNull Document document,
                                          @NotNull PsiJavaDocumentedElement element) {
    PsiDocComment docComment = element.getDocComment();
    if (docComment != null) {
      addToFold(list, docComment, document, true, "/**...*/", docComment.getTextRange(), isCollapseDocCommentByDefault(docComment));
    }
  }

  private boolean addOneLineMethodFolding(@NotNull List<? super FoldingDescriptor> list, @NotNull PsiMethod method) {
    boolean collapseOneLineMethods = JavaCodeFoldingSettings.getInstance().isCollapseOneLineMethods();
    if (!collapseOneLineMethods) {
      return false;
    }

    Document document = method.getContainingFile().getViewProvider().getDocument();
    PsiCodeBlock body = method.getBody();
    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    if (body == null || document == null || nameIdentifier == null) {
      return false;
    }
    if (document.getLineNumber(nameIdentifier.getTextRange().getStartOffset()) !=
        document.getLineNumber(method.getParameterList().getTextRange().getEndOffset())) {
      return false;
    }

    PsiJavaToken lBrace = body.getLBrace();
    PsiJavaToken rBrace = body.getRBrace();
    PsiStatement[] statements = body.getStatements();
    if (lBrace == null || rBrace == null || statements.length != 1) {
      return false;
    }

    PsiStatement statement = statements[0];
    if (statement.textContains('\n')) {
      return false;
    }

    if (!areOnAdjacentLines(lBrace, statement, document) || !areOnAdjacentLines(statement, rBrace, document)) {
      //the user might intend to type at an empty line
      return false;
    }

    int leftStart = method.getParameterList().getTextRange().getEndOffset();
    int bodyStart = body.getTextRange().getStartOffset();
    if (bodyStart > leftStart && !StringUtil.isEmptyOrSpaces(document.getCharsSequence().subSequence(leftStart + 1, bodyStart))) {
      return false;
    }

    int leftEnd = statement.getTextRange().getStartOffset();
    int rightStart = statement.getTextRange().getEndOffset();
    int rightEnd = body.getTextRange().getEndOffset();
    if (leftEnd <= leftStart + 1 || rightEnd <= rightStart + 1) {
      return false;
    }

    String leftText = " { ";
    String rightText = " }";
    if (!fitsRightMargin(method, document, leftStart, rightEnd, rightStart - leftEnd + leftText.length() + rightText.length())) {
      return false;
    }

    FoldingGroup group = FoldingGroup.newGroup("one-liner");
    list.add(new NamedFoldingDescriptor(lBrace.getNode(), new TextRange(leftStart, leftEnd), group, leftText, true, Collections.emptySet()));
    list.add(new NamedFoldingDescriptor(rBrace.getNode(), new TextRange(rightStart, rightEnd), group, rightText, true, Collections.emptySet()));
    return true;
  }

  @Override
  protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    return null;
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    LOG.error("Unknown element:" + node);
    return false;
  }

  private static boolean isCollapseDocCommentByDefault(@NotNull PsiDocComment element) {
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    PsiElement parent = element.getParent();
    if (parent instanceof PsiJavaFile) {
      if (((PsiJavaFile)parent).getName().equals(PsiPackage.PACKAGE_INFO_FILE)) {
        return false;
      }
      PsiElement firstChild = parent.getFirstChild();
      if (firstChild instanceof PsiWhiteSpace) {
        firstChild = firstChild.getNextSibling();
      }
      if (element.equals(firstChild)) {
        return settings.isCollapseFileHeader();
      }
    }
    return settings.isCollapseJavadocs();
  }

  private static boolean isCollapseMethodByDefault(@NotNull PsiMethod element) {
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    if (!settings.isCollapseAccessors() && !settings.isCollapseMethods()) {
      return false;
    }

    if (isSimplePropertyAccessor(element)) {
      return settings.isCollapseAccessors();
    }
    return settings.isCollapseMethods();
  }

  private void addCodeBlockFolds(@NotNull final List<? super FoldingDescriptor> list, @NotNull PsiElement scope,
                                 @NotNull final Set<PsiElement> processedComments,
                                 @NotNull final Document document,
                                 final boolean quick) {
    final boolean dumb = DumbService.isDumb(scope.getProject());
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (dumb || !addClosureFolding(aClass, document, list, processedComments, quick)) {
          addToFold(list, aClass, document, true, getCodeBlockPlaceholder(null), classRange(aClass), JavaCodeFoldingSettings.getInstance().isCollapseInnerClasses());
          addFoldsForClass(list, aClass, document, false, quick);
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (!dumb) {
          addMethodGenericParametersFolding(list, expression, document, quick);
        }

        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (!dumb) {
          addGenericParametersFolding(list, expression, document, quick);
        }

        super.visitNewExpression(expression);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          addToFold(list, expression, document, true, getCodeBlockPlaceholder(expression.getBody()), lambdaRange(expression),
                    JavaCodeFoldingSettings.getInstance().isCollapseAnonymousClasses());
        }
        super.visitLambdaExpression(expression);
      }

      @Override
      public void visitCodeBlock(PsiCodeBlock block) {
        if (Registry.is("java.folding.icons.for.control.flow", true) && block.getStatementCount() > 0) {
          addToFold(list, block, document, false, getCodeBlockPlaceholder(block), block.getTextRange(), false);
        }
        super.visitCodeBlock(block);
      }

      @Override
      public void visitComment(PsiComment comment) {
        addFoldsForComment(list, comment, processedComments);
        super.visitComment(comment);
      }
    });
  }

  private boolean addClosureFolding(@NotNull PsiClass aClass,
                                    @NotNull Document document,
                                    @NotNull List<? super FoldingDescriptor> list,
                                    @NotNull Set<PsiElement> processedComments,
                                    boolean quick) {
    if (!JavaCodeFoldingSettings.getInstance().isCollapseLambdas()) {
      return false;
    }

    if (aClass instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      ClosureFolding closureFolding = ClosureFolding.prepare(anonymousClass, quick, this);
      List<NamedFoldingDescriptor> descriptors = closureFolding == null ? null : closureFolding.process(document);
      if (descriptors != null) {
        list.addAll(descriptors);
        addCodeBlockFolds(list, closureFolding.methodBody, processedComments, document, quick);
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected String rightArrow() {
    return "->";
  }

  boolean fitsRightMargin(@NotNull PsiElement element, @NotNull Document document, int foldingStart, int foldingEnd, int collapsedLength) {
    final int beforeLength = foldingStart - document.getLineStartOffset(document.getLineNumber(foldingStart));
    final int afterLength = document.getLineEndOffset(document.getLineNumber(foldingEnd)) - foldingEnd;
    return isBelowRightMargin(element.getContainingFile(), beforeLength + collapsedLength + afterLength);
  }

  protected abstract boolean isBelowRightMargin(@NotNull PsiFile file, final int lineLength);

  @Override
  protected boolean isCustomFoldingCandidate(@NotNull ASTNode node) {
    return node.getElementType() == JavaTokenType.END_OF_LINE_COMMENT;
  }

  @Override
  protected boolean isCustomFoldingRoot(@NotNull ASTNode node) {
    IElementType nodeType = node.getElementType();
    if (nodeType == JavaElementType.CLASS) {
      ASTNode parent = node.getTreeParent();
      return parent == null || parent.getElementType() != JavaElementType.CLASS;
    }
    return nodeType == JavaElementType.CODE_BLOCK;
  }
}