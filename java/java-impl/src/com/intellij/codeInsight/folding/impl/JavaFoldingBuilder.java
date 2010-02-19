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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaFoldingBuilder extends FoldingBuilderEx implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.JavaFoldingBuilder");
  private static final String SMILEY = "<~>";

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile)) {
      return FoldingDescriptor.EMPTY;
    }
    PsiJavaFile file = (PsiJavaFile) element;

    List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      PsiImportStatementBase[] statements = importList.getAllImportStatements();
      if (statements.length > 1) {
        final TextRange rangeToFold = getRangeToFold(importList);
        if (rangeToFold != null && rangeToFold.getLength() > 1) {
          result.add(new FoldingDescriptor(importList, rangeToFold));
        }
      }
    }

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.checkCanceled();
      addElementsToFold(result, aClass, document, true, quick);
    }

    TextRange range = getFileHeader(file);
    if (range != null && range.getLength() > 1 && document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
      result.add(new FoldingDescriptor(file, range));
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private void addElementsToFold(List<FoldingDescriptor> list, PsiClass aClass, Document document, boolean foldJavaDocs, boolean quick) {
    if (!(aClass.getParent() instanceof PsiJavaFile) || ((PsiJavaFile)aClass.getParent()).getClasses().length > 1) {
      addToFold(list, aClass, document, true);
    }

    PsiDocComment docComment;
    if (foldJavaDocs) {
      docComment = aClass.getDocComment();
      if (docComment != null) {
        addToFold(list, docComment, document, true);
      }
    }
    addAnnotationsToFold(aClass.getModifierList(), list, document);

    PsiElement[] children = aClass.getChildren();
    for (PsiElement child : children) {
      ProgressManager.checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addToFold(list, method, document, true);
        addAnnotationsToFold(method.getModifierList(), list, document);

        if (foldJavaDocs) {
          docComment = method.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          addCodeBlockFolds(body, list, document, quick);
        }
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;
        if (foldJavaDocs) {
          docComment = field.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }
        addAnnotationsToFold(field.getModifierList(), list, document);
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          addCodeBlockFolds(initializer, list, document, quick);
        } else if (field instanceof PsiEnumConstant) {
          addCodeBlockFolds(field, list, document, quick);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        addToFold(list, initializer, document, true);
        addCodeBlockFolds(initializer, list, document, quick);
      }
      else if (child instanceof PsiClass) {
        addElementsToFold(list, (PsiClass)child, document, true, quick);
      }
    }
  }

  @NotNull
  public String getPlaceholderText(@NotNull final ASTNode node) {
    return getPlaceholderText(SourceTreeToPsiMap.treeElementToPsi(node));
  }

  private static String getPlaceholderText(PsiElement element) {
    if (element instanceof PsiImportList) {
      return "...";
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiClass) {
      return "{...}";
    }
    else if (element instanceof PsiDocComment) {
      return "/**...*/";
    }
    else if (element instanceof PsiFile) {
      return "/.../";
    }
    else if (element instanceof PsiAnnotation) {
      return "@{...}";
    }
    if (element instanceof PsiReferenceParameterList) {
      return SMILEY;
    }
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    if (element instanceof PsiNewExpression || element instanceof PsiJavaToken) {
      return settings.isCollapseLambdas();
    }
    if (element instanceof PsiReferenceParameterList) {
      return settings.isCollapseConstructorGenericParameters();
    }

    if (element instanceof PsiImportList) {
      return settings.isCollapseImports();
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiCodeBlock) {
      if (!settings.isCollapseAccessors() && !settings.isCollapseMethods()) {
        return false;
      }
      if (element instanceof PsiMethod && isSimplePropertyAccessor((PsiMethod)element)) {
        return settings.isCollapseAccessors();
      }
      return settings.isCollapseMethods();
    }
    else if (element instanceof PsiAnonymousClass) {
      return settings.isCollapseAnonymousClasses();
    }
    else if (element instanceof PsiClass) {
      return !(element.getParent() instanceof PsiFile) && settings.isCollapseInnerClasses();
    }
    else if (element instanceof PsiDocComment) {
      return settings.isCollapseJavadocs();
    }
    else if (element instanceof PsiJavaFile) {
      return settings.isCollapseFileHeader();
    }
    else if (element instanceof PsiAnnotation) {
      return settings.isCollapseAnnotations();
    }
    else {
      LOG.error("Unknown element:" + element);
      return false;
    }
  }

  private static boolean isSimplePropertyAccessor(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) return false;
    PsiStatement statement = statements[0];
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          return ((PsiReferenceExpression)returnValue).resolve() instanceof PsiField;
        }
      }
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      if (statement instanceof PsiExpressionStatement) {
        PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
        if (expr instanceof PsiAssignmentExpression) {
          PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
          PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
          if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression)lhs).resolve() instanceof PsiField &&
                   ((PsiReferenceExpression)rhs).resolve() instanceof PsiParameter;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  public static TextRange getRangeToFold(PsiElement element) {
    if (element instanceof PsiMethod) {
      if (element instanceof JspHolderMethod) return null;
      PsiCodeBlock body = ((PsiMethod)element).getBody();
      if (body == null) return null;
      return body.getTextRange();
    }
    if (element instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)element).getBody().getTextRange();
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiJavaToken lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiJavaToken rBrace = aClass.getRBrace();
      if (rBrace == null) return null;
      return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
    }
    if (element instanceof PsiJavaFile) {
      return getFileHeader((PsiJavaFile)element);
    }
    if (element instanceof PsiImportList) {
      PsiImportList list = (PsiImportList)element;
      PsiImportStatementBase[] statements = list.getAllImportStatements();
      if (statements.length == 0) return null;
      final PsiElement importKeyword = statements[0].getFirstChild();
      if (importKeyword == null) return null;
      int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
      int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    if (element instanceof PsiDocComment) {
      return element.getTextRange();
    }
    if (element instanceof PsiAnnotation) {
      int startOffset = element.getTextRange().getStartOffset();
      PsiElement last = element;
      while (element instanceof PsiAnnotation) {
        last = element;
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
      }

      return new TextRange(startOffset, last.getTextRange().getEndOffset());
    }
    return null;
  }

  @Nullable
  private static TextRange getFileHeader(PsiJavaFile file) {
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
    if (element.getPrevSibling() instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null || element.equals(first)) return null;
    return new TextRange(first.getTextOffset(), element.getTextOffset());
  }

  private static void addAnnotationsToFold(PsiModifierList modifierList, List<FoldingDescriptor> foldElements, Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        addToFold(foldElements, child, document, false);
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

  private void addCodeBlockFolds(PsiElement scope, final List<FoldingDescriptor> foldElements, final Document document, final boolean quick) {
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        if (!addClosureFolding(aClass, document, foldElements, quick)) {
          addToFold(foldElements, aClass, document, true);
          addElementsToFold(foldElements, aClass, document, false, quick);
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        addMethodGenericParametersFolding(expression, foldElements, document, quick);

        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        addGenericParametersFolding(expression, foldElements, document, quick);

        super.visitNewExpression(expression);
      }
    });
  }

  private static void addMethodGenericParametersFolding(PsiMethodCallExpression expression, List<FoldingDescriptor> foldElements, Document document, boolean quick) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiReferenceParameterList list = methodExpression.getParameterList();
    if (list == null || list.getTextLength() <= 5) {
      return;
    }

    PsiMethodCallExpression element = expression;
    while (true) {
      if (!quick && !resolvesCorrectly(element.getMethodExpression())) return;
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionList) || !(parent.getParent() instanceof PsiMethodCallExpression)) break;
      element = (PsiMethodCallExpression)parent.getParent();
    }

    addTypeParametersFolding(foldElements, document, list, 3, quick);
  }

  private static boolean resolvesCorrectly(PsiReferenceExpression expression) {
    for (final JavaResolveResult result : expression.multiResolve(true)) {
  if (!result.isValidResult()) {
    return false;
  }
}
    return true;
  }

  private static void addGenericParametersFolding(PsiNewExpression expression, List<FoldingDescriptor> foldElements, Document document, boolean quick) {
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

        if (quick || seemsLikeLambda(anonymousClass.getSuperClass())) {
          return;
        }
      }
    }

    if (classReference != null) {
      final PsiReferenceParameterList list = classReference.getParameterList();
      if (list != null) {
        if (quick) {
          final PsiJavaCodeReferenceElement declReference = ((PsiClassReferenceType)declType).getReference();
          final PsiReferenceParameterList declList = declReference.getParameterList();
          if (declList == null || !list.getText().equals(declList.getText())) {
            return;
          }
        } else {
          if (!Arrays.equals(list.getTypeArguments(), parameters)) {
            return;
          }
        }

        addTypeParametersFolding(foldElements, document, list, 5, quick);
      }
    }
  }

  private static void addTypeParametersFolding(List<FoldingDescriptor> foldElements, Document document, PsiReferenceParameterList list,
                                        final int ifLongerThan, boolean quick) {
    if (!quick) {
      for (final PsiType type : list.getTypeArguments()) {
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

    final String text = list.getText();
    if (text.startsWith("<") && text.endsWith(">") && text.length() > ifLongerThan) {
      final TextRange range = list.getTextRange();
      addFoldRegion(foldElements, list, document, true, new TextRange(range.getStartOffset(), range.getEndOffset()));
    }
  }

  private static boolean hasOnlyOneMethod(@NotNull PsiAnonymousClass anonymousClass) {
    if (anonymousClass.getFields().length != 0) {
      return false;
    }
    if (anonymousClass.getInitializers().length != 0) {
      return false;
    }
    if (anonymousClass.getInnerClasses().length != 0) {
      return false;
    }

    return anonymousClass.getMethods().length == 1;
  }

  private boolean addClosureFolding(final PsiClass aClass, final Document document, final List<FoldingDescriptor> foldElements, final boolean quick) {
    if (!JavaCodeFoldingSettings.getInstance().isCollapseLambdas()) {
      return false;
    }

    boolean isClosure = false;
    if (aClass instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      final PsiElement element = anonymousClass.getParent();
      if (element instanceof PsiNewExpression) {
        final PsiNewExpression expression = (PsiNewExpression)element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 0) {
          final PsiMethod[] methods = anonymousClass.getMethods();
          if (hasOnlyOneMethod(anonymousClass) && (quick || seemsLikeLambda(anonymousClass.getBaseClassType().resolve()))) {
            final PsiMethod method = methods[0];
            final PsiCodeBlock body = method.getBody();
            if (body != null) {
              isClosure = true;
              int rangeStart = body.getTextRange().getStartOffset();
              int rangeEnd = body.getTextRange().getEndOffset();
              final PsiJavaToken lbrace = body.getLBrace();
              if (lbrace != null) rangeStart = lbrace.getTextRange().getEndOffset();
              final PsiJavaToken rbrace = body.getRBrace();
              if (rbrace != null) rangeEnd = rbrace.getTextRange().getStartOffset();

              final CharSequence seq = document.getCharsSequence();
              final PsiJavaToken classRBrace = anonymousClass.getRBrace();
              if (classRBrace != null && rbrace != null) {
                final int methodEndLine = document.getLineNumber(rangeEnd);
                final int methodEndLineStart = document.getLineStartOffset(methodEndLine);
                if ("}".equals(seq.subSequence(methodEndLineStart, document.getLineEndOffset(methodEndLine)).toString().trim())) {
                  int classEndStart = classRBrace.getTextRange().getStartOffset();
                  int classEndCol = classEndStart - document.getLineStartOffset(document.getLineNumber(classEndStart));
                  rangeEnd = classEndCol + methodEndLineStart;
                }
              }

              final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());
              int firstLineStart = CharArrayUtil.shiftForward(seq, rangeStart, " \t");
              if (firstLineStart < seq.length() - 1 && seq.charAt(firstLineStart) == '\n') firstLineStart++;

              int lastLineEnd = CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \t");
              if (lastLineEnd > 0 && seq.charAt(lastLineEnd) == '\n') lastLineEnd--;
              if (lastLineEnd < firstLineStart) return false;

              final String baseClassName = quick ? anonymousClass.getBaseClassReference().getReferenceName() : anonymousClass.getBaseClassType().resolve().getName();
              if (lastLineEnd >= seq.length() || firstLineStart >= seq.length() || firstLineStart < 0) {
                LOG.error("llE=" + lastLineEnd + "; fLS=" + firstLineStart + "; len=" + seq.length() + "rE=" + rangeEnd + "; class=" +
                          baseClassName);
              }

              final String params = StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
                public String fun(final PsiParameter psiParameter) {
                  String typeName;
                  if (quick) {
                    typeName = psiParameter.getTypeElement().getText();
                  }
                  else {
                    typeName = psiParameter.getType().getPresentableText();
                  }
                  int genStart = typeName.indexOf('<');
                  int genEnd = typeName.lastIndexOf('>');
                  if (genStart > 0 && genEnd > 0) {
                    typeName = typeName.substring(0, genStart) + typeName.substring(genEnd + 1);
                  }
                  return typeName + " " + psiParameter.getName();
                }
              }, ", ");
              @NonNls final String lambdas = baseClassName + "(" + params + ") {";

              final int closureStart = expression.getTextRange().getStartOffset();
              final int closureEnd = expression.getTextRange().getEndOffset();
              boolean oneLine = false;
              String contents = seq.subSequence(firstLineStart, lastLineEnd).toString();
              if (contents.indexOf('\n') < 0) {
                final int beforeLength = closureStart - document.getLineStartOffset(document.getLineNumber(closureStart));
                final int afterLength = document.getLineEndOffset(document.getLineNumber(closureEnd)) - closureEnd;
                final int resultLineLength = beforeLength + lambdas.length() + contents.length() + 5 + afterLength;

                if (resultLineLength <= settings.RIGHT_MARGIN) {
                  rangeStart = CharArrayUtil.shiftForward(seq, rangeStart, " \n\t");
                  rangeEnd = CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \n\t") + 1;
                  oneLine = true;
                }
              }

              if (rangeStart >= rangeEnd) return false;

              FoldingGroup group = FoldingGroup.newGroup("lambda");

              final String prettySpace = oneLine ? " " : "";

              foldElements.add(
                new FoldingDescriptor(expression.getNode(), new TextRange(closureStart, rangeStart), group) {
                  @Override
                  public String getPlaceholderText() {
                    return lambdas + prettySpace;
                  }
                });

              if (rbrace != null && rangeEnd + 1 < closureEnd) {
                foldElements
                  .add(new FoldingDescriptor(rbrace.getNode(), new TextRange(rangeEnd, closureEnd), group) {
                    @Override
                    public String getPlaceholderText() {
                      return prettySpace + "}";
                    }
                  });
              }
              addCodeBlockFolds(body, foldElements, document, quick);
            }
          }
        }
      }
    }
    return isClosure;
  }

  private static boolean seemsLikeLambda(@Nullable final PsiClass baseClass) {
    if (baseClass == null) return false;

    if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;

    final PsiMethod[] constructors = baseClass.getConstructors();
    boolean hasEmptyConstructor = constructors.length == 0;
    for (final PsiMethod method : constructors) {
      if (method.getParameterList().getParametersCount() == 0) {
        hasEmptyConstructor = true;
        break;
      }
    }

    if (!hasEmptyConstructor) return false;

    for (final PsiMethod method : baseClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return true;
      }
    }

    return false;
  }

  private static boolean addToFold(List<FoldingDescriptor> list, PsiElement elementToFold, Document document, boolean allowOneLiners) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;
    return addFoldRegion(list, elementToFold, document, allowOneLiners, range);
  }

  private static boolean addFoldRegion(final List<FoldingDescriptor> list, final PsiElement elementToFold, final Document document,
                                final boolean allowOneLiners,
                                final TextRange range) {
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return false;

    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());
    // PSI element text ranges may be invalid because of reparse exception (see, for example, IDEA-10617)
    if (range.getStartOffset() < 0 || range.getEndOffset() > fileRange.getEndOffset()) {
      return false;
    }
    if (!allowOneLiners) {
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine < endLine && range.getLength() > 1) {
        list.add(new FoldingDescriptor(elementToFold, range));
        return true;
      }
      return false;
    }
    else {
      if (range.getLength() > getPlaceholderText(elementToFold).length()) {
        list.add(new FoldingDescriptor(elementToFold, range));
        return true;
      }
      return false;
    }
  }
}

