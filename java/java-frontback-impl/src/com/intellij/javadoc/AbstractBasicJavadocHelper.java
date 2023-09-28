// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.*;

public abstract class AbstractBasicJavadocHelper {
  private static final String PARAM_TEXT = "param";

  public static final Pair<JavadocParameterInfo, List<JavadocParameterInfo>> EMPTY
    = new Pair<>(null, Collections.emptyList());
  private static final @NotNull ParentAwareTokenSet TAG_TOKEN_SET = ParentAwareTokenSet.create(BASIC_DOC_TAG,
                                                                                               BASIC_DOC_SNIPPET_TAG,
                                                                                               BASIC_DOC_INLINE_TAG);

  protected AbstractBasicJavadocHelper() {
  }

  /**
   * Tries to navigate caret at the given editor to the target position inserting missing white spaces if necessary.
   *
   * @param position target caret position
   * @param editor   target editor
   * @param project  target project
   */
  public void navigate(@NotNull LogicalPosition position, @NotNull Editor editor, final @NotNull Project project) {
    final Document document = editor.getDocument();
    final CaretModel caretModel = editor.getCaretModel();
    final int endLineOffset = document.getLineEndOffset(position.line);
    final LogicalPosition endLinePosition = editor.offsetToLogicalPosition(endLineOffset);
    if (endLinePosition.column < position.column && !editor.getSettings().isVirtualSpace() && !editor.isViewer()) {
      final String toInsert = StringUtil.repeat(" ", position.column - endLinePosition.column);
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.insertString(endLineOffset, toInsert);
        PsiDocumentManager.getInstance(project).commitDocument(document);
      });
    }
    caretModel.moveToLogicalPosition(position);
  }

  /**
   * Calculates desired position of target javadoc parameter's description start.
   *
   * @param psiFile PSI holder
   * @param data    parsed adjacent javadoc parameters
   * @param anchor  descriptor for the target parameter
   * @return logical position that points to the desired parameter description start location
   */
  public @NotNull LogicalPosition calculateDescriptionStartPosition(@NotNull PsiFile psiFile,
                                                                    @NotNull Collection<? extends JavadocParameterInfo> data,
                                                                    @NotNull JavadocParameterInfo anchor) {
    int descriptionStartColumn = -1;
    int parameterNameEndColumn = -1;
    for (JavadocParameterInfo parameterInfo : data) {
      parameterNameEndColumn = Math.max(parameterNameEndColumn, parameterInfo.parameterNameEndPosition.column);
      if (parameterInfo.parameterDescriptionStartPosition != null) {
        descriptionStartColumn = Math.max(descriptionStartColumn, parameterInfo.parameterDescriptionStartPosition.column);
      }
    }

    int column;

    if (getJdAlignParamComments(psiFile)) {
      column = Math.max(descriptionStartColumn, parameterNameEndColumn);
      if (column <= parameterNameEndColumn) {
        column = parameterNameEndColumn + 1;
      }
    }
    else {
      column = anchor.parameterNameEndPosition.column + 1;
    }
    return new LogicalPosition(anchor.parameterNameEndPosition.line, column);
  }

  protected abstract boolean getJdAlignParamComments(@NotNull PsiFile psiFile);

  /**
   * Returns information about all lines that contain javadoc parameters and are adjacent to the one that holds given offset.
   *
   * @param psiFile PSI holder for the document exposed the given editor
   * @param editor  target editor
   * @param offset  target offset that identifies anchor line to check
   * @return pair like (javadoc info for the line identified by the given offset; list of javadoc parameter infos for
   * adjacent lines if any
   */
  public @NotNull Pair<JavadocParameterInfo, List<JavadocParameterInfo>> parse(@NotNull PsiFile psiFile,
                                                                               @NotNull Editor editor,
                                                                               int offset) {
    List<JavadocParameterInfo> result = new ArrayList<>();
    PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(editor.getDocument());
    final PsiElement elementAtCaret = psiFile.findElementAt(offset);
    if (elementAtCaret == null) {
      return EMPTY;
    }

    ASTNode nodeAtCaret = BasicJavaAstTreeUtil.toNode(elementAtCaret);
    ASTNode tag = BasicJavaAstTreeUtil.getParentOfType(nodeAtCaret, TAG_TOKEN_SET);
    if (tag == null) {
      // Due to javadoc PSI specifics.
      if (BasicJavaAstTreeUtil.isWhiteSpace(nodeAtCaret)) {
        for (ASTNode e = nodeAtCaret.getTreePrev(); e != null && tag == null; e = e.getTreePrev()) {
          tag = BasicJavaAstTreeUtil.getParentOfType(e, TAG_TOKEN_SET, false);
          if (e instanceof PsiWhiteSpace
              || (e.getElementType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
            continue;
          }
          break;
        }
      }
    }
    if (tag == null) {
      return EMPTY;
    }

    JavadocParameterInfo anchorInfo = parse(tag, editor);
    if (anchorInfo == null) {
      return EMPTY;
    }

    // Parse previous parameters.
    for (ASTNode n = tag.getTreePrev(); n != null; n = n.getTreePrev()) {
      JavadocParameterInfo info = parse(n, editor);
      if (info == null) {
        break;
      }
      result.add(0, info);
    }

    result.add(anchorInfo);

    // Parse subsequent parameters.
    for (ASTNode n = tag.getTreeNext(); n != null; n = n.getTreeNext()) {
      JavadocParameterInfo info = parse(n, editor);
      if (info == null) {
        break;
      }
      result.add(info);
    }

    return Pair.create(anchorInfo, result);
  }

  private static @Nullable JavadocParameterInfo parse(@NotNull ASTNode astNode, @NotNull Editor editor) {
    final ASTNode tag = BasicJavaAstTreeUtil.getParentOfType(astNode, TAG_TOKEN_SET, false);
    if (tag == null || !PARAM_TEXT.equals(BasicJavaAstTreeUtil.getTagName(tag))) {
      return null;
    }

    final ASTNode paramRef = BasicJavaAstTreeUtil.findChildByType(tag, BASIC_DOC_TAG_VALUE_ELEMENT,
                                                                  BASIC_DOC_METHOD_OR_FIELD_REF,
                                                                  BASIC_DOC_PARAMETER_REF,
                                                                  BASIC_DOC_SNIPPET_TAG_VALUE);
    if (paramRef == null) {
      return null;
    }

    for (ASTNode node = paramRef.getTreeNext(); node != null; node = node.getTreeNext()) {
      final IElementType elementType = node.getElementType();
      if (elementType == JavaDocTokenType.DOC_COMMENT_DATA) {
        return new JavadocParameterInfo(
          editor.offsetToLogicalPosition(paramRef.getTextRange().getEndOffset()),
          editor.offsetToLogicalPosition(node.getTextRange().getStartOffset()),
          editor.getDocument().getLineNumber(node.getTextRange().getEndOffset())
        );
      }
      else if (elementType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        break;
      }
    }
    return new JavadocParameterInfo(
      editor.offsetToLogicalPosition(paramRef.getTextRange().getEndOffset()),
      null,
      editor.getDocument().getLineNumber(paramRef.getTextRange().getEndOffset())
    );
  }

  /**
   * Encapsulates information about source code line that holds javadoc parameter.
   */
  public static class JavadocParameterInfo {

    /**
     * Logical position that points to location just after javadoc parameter name.
     * <p/>
     * Example:
     * <pre>
     *   /**
     *    * @param i[X]  description
     *    *&#47;
     * </pre>
     */
    public final @NotNull LogicalPosition parameterNameEndPosition;
    public final @Nullable LogicalPosition parameterDescriptionStartPosition;
    /** Last logical line occupied by the current javadoc parameter. */
    public final int lastLine;

    public JavadocParameterInfo(@NotNull LogicalPosition parameterNameEndPosition,
                                @Nullable LogicalPosition parameterDescriptionStartPosition,
                                int lastLine) {
      this.parameterNameEndPosition = parameterNameEndPosition;
      this.parameterDescriptionStartPosition = parameterDescriptionStartPosition;
      this.lastLine = lastLine;
    }

    @Override
    public String toString() {
      return "name end: " + parameterNameEndPosition + ", description start: " + parameterDescriptionStartPosition;
    }
  }
}
