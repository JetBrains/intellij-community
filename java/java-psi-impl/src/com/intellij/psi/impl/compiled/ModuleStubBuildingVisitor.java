/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ModuleVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.Arrays;

import static com.intellij.psi.impl.java.stubs.JavaStubElementTypes.*;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.containers.ContainerUtil.map2Array;

public class ModuleStubBuildingVisitor extends ClassVisitor {
  private static final Function<String, String> NAME_MAPPER = name1 -> name1.replace('/', '.');

  private final PsiJavaFileStub myParent;
  private PsiJavaModuleStub myResult;
  private PsiModifierListStubImpl myModList;

  public ModuleStubBuildingVisitor(PsiJavaFileStub parent) {
    super(Opcodes.API_VERSION);
    myParent = parent;
  }

  public PsiJavaModuleStub getResult() {
    return myResult;
  }

  @Override
  public ModuleVisitor visitModule(String name, int access, String version) {
    myResult = new PsiJavaModuleStubImpl(myParent, name);

    myModList = new PsiModifierListStubImpl(myResult, moduleFlags(access));

    return new ModuleVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitRequire(String module, int access, String version) {
        if (!isGenerated(access)) {
          PsiRequiresStatementStubImpl statementStub = new PsiRequiresStatementStubImpl(myResult, module);
          new PsiModifierListStubImpl(statementStub, requiresFlags(access));
        }
      }

      @Override
      public void visitExport(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          new PsiPackageAccessibilityStatementStubImpl(
            myResult, EXPORTS_STATEMENT, NAME_MAPPER.fun(packageName), modules == null ? null : Arrays.asList(modules));
        }
      }

      @Override
      public void visitOpen(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          new PsiPackageAccessibilityStatementStubImpl(
            myResult, OPENS_STATEMENT, NAME_MAPPER.fun(packageName), modules == null ? null : Arrays.asList(modules));
        }
      }

      @Override
      public void visitUse(String service) {
        new PsiUsesStatementStubImpl(myResult, NAME_MAPPER.fun(service));
      }

      @Override
      public void visitProvide(String service, String... providers) {
        PsiProvidesStatementStubImpl statementStub = new PsiProvidesStatementStubImpl(myResult, NAME_MAPPER.fun(service));
        String[] names = map2Array(providers, String.class, NAME_MAPPER);
        new PsiClassReferenceListStubImpl(PROVIDES_WITH_LIST, statementStub, names.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : names);
      }
    };
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    return StubBuildingVisitor.getAnnotationTextCollector(desc, text -> new PsiAnnotationStubImpl(myModList, text));
  }

  private static boolean isGenerated(int access) {
    return isSet(access, Opcodes.ACC_SYNTHETIC) || isSet(access, Opcodes.ACC_MANDATED);
  }

  private static int moduleFlags(int access) {
    return isSet(access, Opcodes.ACC_OPEN) ? ModifierFlags.OPEN_MASK : 0;
  }

  private static int requiresFlags(int access) {
    int flags = 0;
    if (isSet(access, Opcodes.ACC_TRANSITIVE)) flags |= ModifierFlags.TRANSITIVE_MASK;
    if (isSet(access, Opcodes.ACC_STATIC_PHASE)) flags |= ModifierFlags.STATIC_MASK;
    return flags;
  }
}