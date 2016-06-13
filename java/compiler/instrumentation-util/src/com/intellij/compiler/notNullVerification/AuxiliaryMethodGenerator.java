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
package com.intellij.compiler.notNullVerification;

import org.jetbrains.org.objectweb.asm.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * @author peter
 */
class AuxiliaryMethodGenerator {
  private final Set<String> myExistingMethods = new HashSet<String>();
  private final ClassReader myOriginalClass;
  private final Map<ReportingMethod, String> myReportingMethods = new HashMap<ReportingMethod, String>();

  AuxiliaryMethodGenerator(ClassReader originalClass) {
    myOriginalClass = originalClass;
  }

  String suggestReportingMethod(ReportingMethod reportingMethod) {
    String name = myReportingMethods.get(reportingMethod);
    if (name == null) {
      myExistingMethods.add(name = suggestUniqueName());
      myReportingMethods.put(reportingMethod, name);
    }
    return name;
  }

  private String suggestUniqueName() {
    ensureExistingMethodsPopulated();

    for (int i = 0;; i++) {
      String name = "$$$reportNull$$$" + i;
      if (!myExistingMethods.contains(name)) {
        return name;
      }
    }
  }

  private void ensureExistingMethodsPopulated() {
    if (myExistingMethods.isEmpty()) {
      myOriginalClass.accept(new ClassVisitor(ASM5) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
          myExistingMethods.add(name);
          return null;
        }
      }, 0);
    }
  }

  void generateReportingMethods(ClassVisitor cw) {
    for (Map.Entry<ReportingMethod, String> entry : myReportingMethods.entrySet()) {
      entry.getKey().generateMethod(cw, entry.getValue());
    }
  }
}
