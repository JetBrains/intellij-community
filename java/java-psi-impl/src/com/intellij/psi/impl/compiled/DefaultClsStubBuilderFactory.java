/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.util.cls.ClsFormatException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;

import java.io.IOException;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DefaultClsStubBuilderFactory extends ClsStubBuilderFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.DefaultClsStubBuilderFactory");

  @Override
  public PsiFileStub buildFileStub(VirtualFile vFile, byte[] bytes) throws ClsFormatException {
    final PsiJavaFileStubImpl file = new PsiJavaFileStubImpl("do.not.know.yet", true);
    try {
      final ClassReader reader = new ClassReader(bytes);
      final StubBuildingVisitor<VirtualFile> classVisitor =
        new StubBuildingVisitor<VirtualFile>(vFile, VirtualFileInnerClassStrategy.INSTANCE, file, 0, null);
      try {
        reader.accept(classVisitor, ClassReader.SKIP_FRAMES);
      }
      catch (OutOfOrderInnerClassException e) {
        return null;
      }

      @SuppressWarnings("unchecked") final PsiClassStub<PsiClass> result = (PsiClassStub<PsiClass>)classVisitor.getResult();
      if (result == null) return null;

      file.setPackageName(getPackageName(result));
      return file;
    }
    catch (Exception e) {
      LOG.debug(vFile.getPath(), e);
      throw new ClsFormatException();
    }
  }

  @Override
  public boolean canBeProcessed(VirtualFile file, byte[] bytes) {
    return true;
  }

  @Override
  public boolean isInnerClass(VirtualFile file) {
    String name = file.getNameWithoutExtension();
    int len = name.length();
    int idx = name.indexOf('$');

    while (idx > 0) {
      if (idx + 1 < len && Character.isDigit(name.charAt(idx + 1))) return true;
      idx = name.indexOf('$', idx + 1);
    }

    return false;
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
