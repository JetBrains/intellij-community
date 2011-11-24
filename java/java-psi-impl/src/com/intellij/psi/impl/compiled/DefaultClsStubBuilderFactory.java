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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;

import java.io.IOException;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DefaultClsStubBuilderFactory extends ClsStubBuilderFactory {
  @Override
  public PsiFileStub buildFileStub(VirtualFile vFile, byte[] bytes) throws ClsFormatException {
    final PsiJavaFileStubImpl file = new PsiJavaFileStubImpl("do.not.know.yet", true);
    try {
      ClassReader reader = new ClassReader(bytes);

      final StubBuildingVisitor<VirtualFile>
        classVisitor = new StubBuildingVisitor<VirtualFile>(vFile, VirtualFileInnerClassStrategy.INSTANCE, file, 0);
      reader.accept(classVisitor, 0);

      final PsiClassStub result = classVisitor.getResult();
      if (result == null) return null;

      //noinspection unchecked
      file.setPackageName(getPackageName(result));
    }
    catch (Exception e) {
      throw new ClsFormatException();
    }
    return file;
  }

  @Override
  public boolean canBeProcessed(VirtualFile file, byte[] bytes) {
    return true;
  }

  @Override
  public boolean isInnerClass(VirtualFile file) {
    return isInner(file.getNameWithoutExtension(), new ParentDirectory(file));
  }

  static boolean isInner(final String name, final Directory directory) {
    return isInner(name, 0, directory);
  }

  private static boolean isInner(final String name, final int from, final Directory directory) {
    final int index = name.indexOf('$', from);
    return index != -1 && (containsPart(directory, name, index) || isInner(name, index + 1, directory));
  }

  private static boolean containsPart(Directory directory, String name, int endIndex) {
    return endIndex > 0 && directory.contains(name.substring(0, endIndex));
  }

  interface Directory {
    boolean contains(String name);
  }

  private static class ParentDirectory implements Directory {
    private final VirtualFile myDirectory;
    private final String myExtension;

    private ParentDirectory(final VirtualFile file) {
      myDirectory = file.getParent();
      myExtension = file.getExtension();
    }

    @Override
    public boolean contains(final String name) {
      final String fullName = myExtension == null ? name : name + "." + myExtension;
      return myDirectory != null && myDirectory.findChild(fullName) != null;
    }
  }

  private static class VirtualFileInnerClassStrategy implements InnerClassSourceStrategy<VirtualFile> {
    public static VirtualFileInnerClassStrategy INSTANCE = new VirtualFileInnerClassStrategy();

    @Nullable
    @Override
    public VirtualFile findInnerClass(String innerName, VirtualFile outerClass) {
      final String baseName = outerClass.getNameWithoutExtension();
      final VirtualFile dir = outerClass.getParent();
      assert dir != null;

      return dir.findChild(baseName + "$" + innerName + ".class");
    }

    @Nullable
    @Override
    public ClassReader readerForInnerClass(VirtualFile innerClass) {
      try {
        return new ClassReader(innerClass.contentsToByteArray());
      }
      catch (IOException e) {
        return null;
      }
    }
  }

  private static String getPackageName(final PsiClassStub<PsiClass> result) {
    final String fqn = result.getQualifiedName();
    final String shortName = result.getName();
    if (fqn == null || Comparing.equal(shortName, fqn)) {
      return "";
    }

    return fqn.substring(0, fqn.lastIndexOf('.'));
  }
}
