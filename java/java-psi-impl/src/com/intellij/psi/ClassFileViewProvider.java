// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;

import static com.intellij.psi.impl.compiled.ClsFileImpl.EMPTY_ATTRIBUTES;

public class ClassFileViewProvider extends SingleRootFileViewProvider {
  private static final Key<Boolean> IS_INNER_CLASS = Key.create("java.is.inner.class.key");

  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    this(manager, file, true);
  }

  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
    super(manager, file, eventSystemEnabled, JavaLanguage.INSTANCE);
  }

  @Override
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    FileIndexFacade fileIndex = FileIndexFacade.getInstance(project);
    if (!fileIndex.isInLibraryClasses(file) && fileIndex.isInSource(file)) {
      return new PsiBinaryFileImpl(getManager(), this);
    }

    // skip inner, anonymous, missing and corrupted classes
    try {
      if (!isInnerClass(file)) {
        return new ClsFileImpl(this);
      }
    }
    catch (Exception e) {
      Logger.getInstance(ClassFileViewProvider.class).debug(file.getPath(), e);
    }

    return null;
  }

  public static boolean isInnerClass(@NotNull VirtualFile file) {
    return isInnerClass(file, (ClassReader)null);
  }

  /**
   * @deprecated use {@link #isInnerClass(VirtualFile)} or {@link #isInnerClass(VirtualFile, ClassReader)} 
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static boolean isInnerClass(@NotNull VirtualFile file, byte @NotNull [] content) {
    return isInnerClass(file, (ClassReader) null);
  }

  /**
   * @param file virtual file
   * @param reader ready class reader for this file, null, if not available
   * @return true if the class is inner class
   */
  public static boolean isInnerClass(@NotNull VirtualFile file, @Nullable ClassReader reader) {
    String name = file.getNameWithoutExtension();
    int p = name.lastIndexOf('$', name.length() - 2);
    if (p <= 0) return false;

    Boolean isInner = IS_INNER_CLASS.get(file);
    if (isInner != null) return isInner;

    if (reader == null) {
      try {
        reader = new ClassReader(file.contentsToByteArray(false));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    final String className = reader.getClassName();
    class MyVisitor extends ClassVisitor {
      boolean isInner;
      
      MyVisitor() {
        super(Opcodes.API_VERSION);
      }

      @Override
      public void visitOuterClass(String owner, String name, String desc) {
        isInner = true;
      }

      @Override
      public void visitInnerClass(String name, String outer, String inner, int access) {
        if (className.equals(name)) {
          isInner = true;
        }
      }
    }
    MyVisitor visitor = new MyVisitor();
    reader.accept(visitor, EMPTY_ATTRIBUTES, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

    IS_INNER_CLASS.set(file, visitor.isInner);
    return visitor.isInner;
  }

  @Override
  public @NotNull SingleRootFileViewProvider createCopy(@NotNull VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return findElementAt(offset, getBaseLanguage());
  }

  @Override
  public PsiElement findElementAt(int offset, @NotNull Language language) {
    PsiFile file = getPsi(language);
    if (file instanceof PsiCompiledFile) file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    return findElementAt(file, offset);
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return findReferenceAt(offset, getBaseLanguage());
  }

  @Override
  public @Nullable PsiReference findReferenceAt(int offset, @NotNull Language language) {
    PsiFile file = getPsi(language);
    if (file instanceof PsiCompiledFile) file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    return findReferenceAt(file, offset);
  }
}