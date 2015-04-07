/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.compiler.instrumentation;

import org.jetbrains.org.objectweb.asm.AnnotationVisitor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.TypePath;

/**
 * To be used together with FailSafeClassReader: adds null checks for labels describing annotation visibility range.
 * For incorrectly generated annotations FailSafeClassReader returns null labels. Local variables annotations with null labels 
 * will be ignored by this visitor.
 */
public class FailSafeMethodVisitor extends MethodVisitor {
  public FailSafeMethodVisitor(int api, MethodVisitor mv) {
    super(api, mv);
  }

  public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
    for (Label aStart : start) {
      if (aStart == null) {
        return null;
      }
    }
    for (Label anEnd : end) {
      if (anEnd == null) {
        return null;
      }
    }
    return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible);
  }
}
