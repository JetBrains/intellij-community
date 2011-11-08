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

import com.intellij.openapi.extensions.Extensions;
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
public class ClsStubBuilder {
  private ClsStubBuilder() { }

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

  @Nullable
  public static PsiFileStub build(final VirtualFile vFile, byte[] bytes) throws ClsFormatException {
    final ClsStubBuilderFactory[] factories = Extensions.getExtensions(ClsStubBuilderFactory.EP_NAME);
    for (ClsStubBuilderFactory factory : factories) { 
      if (factory.canBeProcessed(vFile, bytes)) {
        return factory.buildFileStub(vFile, bytes);
      }
    }

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

  private static String getPackageName(final PsiClassStub<PsiClass> result) {
    final String fqn = result.getQualifiedName();
    final String shortName = result.getName();
    if (fqn == null || Comparing.equal(shortName, fqn)) {
      return "";
    }

    return fqn.substring(0, fqn.lastIndexOf('.'));
  }

}
