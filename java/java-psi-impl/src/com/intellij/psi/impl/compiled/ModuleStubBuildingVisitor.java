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
package com.intellij.psi.impl.compiled;

import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.java.stubs.impl.PsiExportsStatementStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaModuleStubImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiRequiresStatementStubImpl;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ModuleVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.Arrays;

import static com.intellij.util.BitUtil.isSet;

public class ModuleStubBuildingVisitor extends ClassVisitor {
  private static final int ACC_TRANSITIVE   = 0x0010;
  private static final int ACC_STATIC_PHASE = 0x0020;

  private final PsiJavaFileStub myParent;
  private final String myModuleName;
  private PsiJavaModuleStub myResult;

  public ModuleStubBuildingVisitor(PsiJavaFileStub parent, String moduleName) {
    super(Opcodes.API_VERSION);
    myParent = parent;
    myModuleName = moduleName;
  }

  public PsiJavaModuleStub getResult() {
    return myResult;
  }

  @Override
  public ModuleVisitor visitModule() {
    myResult = new PsiJavaModuleStubImpl(myParent, myModuleName);
    return new ModuleVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitRequire(String module, int access) {
        if (!isSet(access, Opcodes.ACC_SYNTHETIC) && !isSet(access, Opcodes.ACC_MANDATED)) {
          boolean isPublic = isSet(access, ACC_TRANSITIVE) | isSet(access, Opcodes.ACC_PUBLIC);
          new PsiRequiresStatementStubImpl(myResult, module, isPublic, isSet(access, ACC_STATIC_PHASE));
        }
      }

      @Override
      public void visitExport(String packageName, String... modules) {
        new PsiExportsStatementStubImpl(myResult, packageName.replace('/', '.'), modules == null ? null : Arrays.asList(modules));
      }
    };
  }
}