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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

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

    // skip inner & anonymous
    if (isInnerClass(file)) return null;

    return new ClsFileImpl(this);
  }

  public static boolean isInnerClass(@NotNull VirtualFile file) {
    String name = file.getNameWithoutExtension();
    int index = name.lastIndexOf('$');
    if (index > 0 && index < name.length() - 1) {
      String parentName = name.substring(0, index), childName = name.substring(index + 1);
      if (file.getParent().findChild(parentName + ".class") != null) {
        return isInnerClass(file, parentName, childName);
      }
    }
    return false;
  }

  private static boolean isInnerClass(VirtualFile file, final String parentName, final String childName) {
    Boolean isInner = IS_INNER_CLASS.get(file);
    if (isInner != null) return isInner;

    final Ref<Boolean> ref = Ref.create(Boolean.FALSE);
    try {
      new ClassReader(file.contentsToByteArray(false)).accept(new ClassVisitor(Opcodes.ASM5) {
        @Override
        public void visitOuterClass(String owner, String name, String desc) {
          ref.set(Boolean.TRUE);
          throw new ProcessCanceledException();
        }

        @Override
        public void visitInnerClass(String name, String outer, String inner, int access) {
          if ((inner == null || childName.equals(inner)) && outer != null && parentName.equals(outer.substring(outer.lastIndexOf('/') + 1)) ||
              inner == null && outer == null && name.substring(name.lastIndexOf('/') + 1).equals(parentName + '$' + childName)) {
            ref.set(Boolean.TRUE);
            throw new ProcessCanceledException();
          }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
          throw new ProcessCanceledException();
        }
      }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    catch (ProcessCanceledException ignored) { }
    catch (Exception e) {
      Logger.getInstance(ClassFileViewProvider.class).warn(file.getPath(), e);
    }

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