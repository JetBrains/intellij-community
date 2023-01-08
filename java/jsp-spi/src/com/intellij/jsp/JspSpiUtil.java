// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.jsp.BaseJspFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class JspSpiUtil {
  private static final Logger LOG = Logger.getInstance(JspSpiUtil.class);
  @NonNls private static final String JAR_EXTENSION = "jar";

  @Nullable
  private static JspSpiUtil getJspSpiUtil() {
    return ApplicationManager.getApplication().getService(JspSpiUtil.class);
  }

  public static int escapeCharsInJspContext(JspFile file, int offset, String toEscape) throws IncorrectOperationException {
    final JspSpiUtil util = getJspSpiUtil();
    return util != null ? util._escapeCharsInJspContext(file, offset, toEscape) : 0;
  }

  protected abstract int _escapeCharsInJspContext(JspFile file, int offset, String toEscape) throws IncorrectOperationException;

  public static void visitAllIncludedFilesRecursively(BaseJspFile jspFile, Processor<? super BaseJspFile> visitor) {
    final JspSpiUtil util = getJspSpiUtil();
    if (util != null) {
      util._visitAllIncludedFilesRecursively(jspFile, visitor);
    }
  }

  protected abstract void _visitAllIncludedFilesRecursively(BaseJspFile jspFile, Processor<? super BaseJspFile> visitor);

  @Nullable
  public static PsiElement resolveMethodPropertyReference(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? null : util._resolveMethodPropertyReference(reference, resolvedClass, readable);
  }

  @Nullable
  protected abstract PsiElement _resolveMethodPropertyReference(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable);

  public static Object @NotNull [] getMethodPropertyReferenceVariants(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : util._getMethodPropertyReferenceVariants(reference, resolvedClass, readable);
  }

  protected abstract Object[] _getMethodPropertyReferenceVariants(@NotNull PsiReference reference, @Nullable PsiClass resolvedClass, boolean readable);

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

  public static PsiFile[] getIncludingFiles(@NotNull JspFile jspFile) {
    final JspSpiUtil util = getJspSpiUtil();
    return util == null ? PsiFile.EMPTY_ARRAY : util._getIncludingFiles(jspFile);
  }

  protected abstract PsiFile[] _getIncludingFiles(@NotNull PsiFile file);

  protected abstract PsiFile @NotNull [] _getIncludedFiles(@NotNull final JspFile jspFile);

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
    return buildUrls(virtualFile, module, true);
  }

  public static List<URL> buildUrls(@Nullable final VirtualFile virtualFile, @Nullable final Module module, boolean includeModuleOutput) {
    final List<URL> urls = new ArrayList<>();
    processClassPathItems(virtualFile, module, file -> addUrl(urls, file), includeModuleOutput);
    return urls;
  }

  public static List<Path> buildFiles(@Nullable VirtualFile virtualFile, @Nullable Module module, boolean includeModuleOutput) {
    List<Path> result = new ArrayList<>();
    processClassPathItems(virtualFile, module, file -> {
      if (file != null && file.isValid()) {
        Path path = file.getFileSystem().getNioPath(file);
        if (path != null) {
          result.add(path);
        }
      }
    }, includeModuleOutput);
    return result;
  }

  public static void processClassPathItems(final VirtualFile virtualFile, final Module module, final Consumer<? super VirtualFile> consumer) {
    processClassPathItems(virtualFile, module, consumer, true);
  }

  public static void processClassPathItems(final VirtualFile virtualFile, final Module module, final Consumer<? super VirtualFile> consumer,
                                           boolean includeModuleOutput) {
    if (isJarFile(virtualFile)){
      consumer.consume(virtualFile);
    }

    if (module != null) {
      OrderEnumerator enumerator = ModuleRootManager.getInstance(module).orderEntries().recursively();
      if (!includeModuleOutput) {
        enumerator = enumerator.withoutModuleSourceEntries();
      }
      for (VirtualFile root : enumerator.getClassesRoots()) {
        final VirtualFile file;
        if (root.getFileSystem().getProtocol().equals(JarFileSystem.PROTOCOL)) {
          file = JarFileSystem.getInstance().getVirtualFileForJar(root);
        }
        else {
          file = root;
        }
        consumer.consume(file);
      }
    }
  }

  private static void addUrl(List<? super URL> urls, VirtualFile file) {
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
