/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.jsp;

import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class JspSpiUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.jsp.tagLibrary.JspTagInfoImpl");
  @NonNls private static final String JAR_EXTENSION = "jar";

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

  public static boolean isIncludedOrIncludesSomething(@NotNull JspFile file) {
    return isIncludingAnything(file) || isIncluded(file);
  }

  public static boolean isIncluded(@NotNull JspFile jspFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null && util._isIncluded(jspFile);
  }

  public abstract boolean _isIncluded(@NotNull final JspFile jspFile);

  public static boolean isIncludingAnything(@NotNull JspFile jspFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null && util._isIncludingAnything(jspFile);
  }

  protected abstract boolean _isIncludingAnything(@NotNull final JspFile jspFile);

  public static PsiFile[] getIncludedFiles(@NotNull JspFile jspFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? PsiFile.EMPTY_ARRAY : util._getIncludedFiles(jspFile);
  }

  @NotNull
  protected abstract PsiFile[] _getIncludedFiles(@NotNull final JspFile jspFile);

  @NotNull
  protected abstract PsiFile[] _getReferencingFiles(JspFile jspFile);

  @Nullable
  public static Lexer createElLexer() {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._createElLexer();
  }

  protected abstract Lexer _createElLexer();

  @Nullable
  public static EditorHighlighter createJSPHighlighter(EditorColorsScheme settings, Project project, VirtualFile virtualFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._createJSPHighlighter(settings, project, virtualFile);
  }

  protected abstract EditorHighlighter _createJSPHighlighter(EditorColorsScheme settings, Project project, VirtualFile virtualFile);

  @Nullable
  public static CompletionData createJspCompletionData() {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._createJspCompletionData() : null;
  }

  protected abstract CompletionData _createJspCompletionData();

  public static PsiReference[] getReferencesForXmlCommentInJspx(XmlComment comment) {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util.getReferencesFromComment(comment) : PsiReference.EMPTY_ARRAY;
  }

  protected abstract PsiReference[] getReferencesFromComment(XmlComment comment);

  public static boolean isJavaContext(PsiElement position) {
    if(PsiTreeUtil.getContextOfType(position, JspClass.class, false) != null) return true;
    return false;
  }

  public static boolean isJarFile(@Nullable VirtualFile file) {
    if (file != null){
      final String ext = file.getExtension();
      if(ext != null && ext.equalsIgnoreCase(JAR_EXTENSION)) {
        return true;
      }
    }

    return false;
  }

  public static List<URL> buildUrls(@Nullable final VirtualFile virtualFile, @Nullable final Module module) {
    List<URL> urls = new ArrayList<URL>();

    if (isJarFile(virtualFile)){
      addUrl(urls, virtualFile);
    }

    if (module != null) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getFiles(OrderRootType.CLASSES_AND_OUTPUT);
      for (VirtualFile file1 : files) {
        final VirtualFile file;
        if (file1.getFileSystem().getProtocol().equals(JarFileSystem.PROTOCOL)) {
          file = JarFileSystem.getInstance().getVirtualFileForJar(file1);
        }
        else {
          file = file1;
        }
        addUrl(urls, file);
      }
    }
    return urls;
  }

  private static void addUrl(List<URL> urls, VirtualFile file) {
    if (file == null || !file.isValid()) return;
    final URL url = getUrl(file);
    if (url != null) {
      urls.add(url);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  private static URL getUrl(VirtualFile file) {
    if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) return null;

    String path = file.getPath();
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - 2);
    }

    String url;
    if (SystemInfo.isWindows) {
      url = "file:/" + path;
    }
    else {
      url = "file://" + path;
    }

    if (file.isDirectory() && !(file.getFileSystem() instanceof JarFileSystem)) url += "/";


    try {
      return new URL(url);
    }
    catch (MalformedURLException e) {
      LOG.error(e);
      return null;
    }
  }
}
