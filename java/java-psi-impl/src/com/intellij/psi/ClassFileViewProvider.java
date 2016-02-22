/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;

import static com.intellij.psi.impl.compiled.ClsFileImpl.EMPTY_ATTRIBUTES;

/**
 * @author max
 */
public class ClassFileViewProvider extends SingleRootFileViewProvider {
  private static final Key<Boolean> IS_INNER_CLASS = Key.create("java.is.inner.class.key");

  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
    super(manager, file, eventSystemEnabled, JavaClassFileType.INSTANCE);
  }

  @Override
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    FileIndexFacade fileIndex = ServiceManager.getService(project, FileIndexFacade.class);
    if (!fileIndex.isInLibraryClasses(file) && fileIndex.isInSource(file)) {
      return new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }

    // skip inner, anonymous, missing and corrupted classes
    try {
      if (!isInnerClass(file, file.contentsToByteArray(false))) {
        return new ClsFileImpl(this);
      }
    }
    catch (Exception e) {
      Logger.getInstance(ClassFileViewProvider.class).debug(file.getPath(), e);
    }

    return null;
  }

  /** @deprecated use {@link #isInnerClass(VirtualFile, byte[])} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public static boolean isInnerClass(@NotNull VirtualFile file) {
    try {
      String name = file.getNameWithoutExtension();
      int p = name.lastIndexOf('$', name.length() - 2);
      return p > 0 && detectInnerClass(file, file.contentsToByteArray(false));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isInnerClass(@NotNull VirtualFile file, @NotNull byte[] content) {
    String name = file.getNameWithoutExtension();
    int p = name.lastIndexOf('$', name.length() - 2);
    return p > 0 && detectInnerClass(file, content);
  }

  private static boolean detectInnerClass(VirtualFile file, byte[] content) {
    Boolean isInner = IS_INNER_CLASS.get(file);
    if (isInner != null) return isInner;

    ClassReader reader = new ClassReader(content);
    final Ref<Boolean> ref = Ref.create(Boolean.FALSE);
    final String className = reader.getClassName();
    reader.accept(new ClassVisitor(Opcodes.ASM5) {
      @Override
      public void visitOuterClass(String owner, String name, String desc) {
        ref.set(Boolean.TRUE);
      }

      @Override
      public void visitInnerClass(String name, String outer, String inner, int access) {
        if (className.equals(name)) {
          ref.set(Boolean.TRUE);
        }
      }
    }, EMPTY_ATTRIBUTES, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

    isInner = ref.get();
    IS_INNER_CLASS.set(file, isInner);
    return isInner;
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}