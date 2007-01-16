/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.jsp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class JspSpiUtil {

  @Nullable
  private static JspSpiUtil getJspSpiUtil() {
    return ApplicationManager.getApplication().getComponent(JspSpiUtil.class);
  }

  @Nullable
  public static IChameleonElementType createSimpleChameleon(@NonNls String debugName, IElementType start, IElementType end, final int startLength) {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._createSimpleChameleon(debugName, start, end, startLength) : null;
  }

  protected abstract IChameleonElementType _createSimpleChameleon(@NonNls String debugName, IElementType start, IElementType end,
                                                                  final int startLength);

  @Nullable
  public static IFileElementType createTemplateType() {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._createTemplateType() : null;
  }

  protected abstract IFileElementType _createTemplateType();

  public static int escapeCharsInJspContext(JspFile file, int offset, String toEscape) throws IncorrectOperationException {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._escapeCharsInJspContext(file, offset, toEscape) : 0;
  }

  protected abstract int _escapeCharsInJspContext(JspFile file, int offset, String toEscape) throws IncorrectOperationException;

  public static void visitAllIncludedFilesRecursively(JspFile jspFile, PsiElementVisitor visitor) {
    final JspSpiUtil util = getJspSpiUtil();
    if (util != null) {
      util._visitAllIncludedFilesRecursively(jspFile, visitor);
    }
  }

  protected abstract void _visitAllIncludedFilesRecursively(JspFile jspFile, PsiElementVisitor visitor);

  @Nullable
  public static JspDirectiveKind getDirectiveKindByTag(XmlTag tag) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._getDirectiveKindByTag(tag);
  }

  @Nullable
  protected abstract JspDirectiveKind _getDirectiveKindByTag(XmlTag tag);

  @Nullable
  public static PsiElement resolveMethodPropertyReference(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._resolveMethodPropertyReference(reference, resolvedClass, readable);
  }

  @Nullable
  protected abstract PsiElement _resolveMethodPropertyReference(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable);

  @NotNull
  public static Object[] getMethodPropertyReferenceVariants(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : util._getMethodPropertyReferenceVariants(reference, resolvedClass, readable);
  }

  protected abstract Object[] _getMethodPropertyReferenceVariants(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable);

  @NotNull
  public static PsiFile[] getReferencingFiles(JspFile jspFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? PsiFile.EMPTY_ARRAY : util._getReferencingFiles(jspFile);
  }

  @NotNull
  protected abstract PsiFile[] _getReferencingFiles(JspFile jspFile);

  @Nullable
  public static Lexer createElLexer() {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._createElLexer();
  }

  protected abstract Lexer _createElLexer();

}
