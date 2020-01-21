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
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.ArrayUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaConstructorBodyWithSuperCallGenerator implements ConstructorBodyGenerator {
  @Override
  public void generateFieldInitialization(@NotNull StringBuilder buffer,
                                          PsiField @NotNull [] fields,
                                          PsiParameter @NotNull [] parameters,
                                          @NotNull Collection<String> existingNames) {
    if (fields.length > 0 && generateRecordDelegatingConstructor(buffer, fields, parameters)) {
      return;
    }
    ConstructorBodyGenerator.super.generateFieldInitialization(buffer, fields, parameters, existingNames);
  }


  private boolean generateRecordDelegatingConstructor(@NotNull StringBuilder buffer,
                                                      PsiField @NotNull [] fields,
                                                      PsiParameter @NotNull [] parameters) {
    PsiRecordComponent component = JavaPsiRecordUtil.getComponentForField(fields[0]);
    if (component == null) return false;
    PsiClass recordClass = component.getContainingClass();
    if (recordClass == null) return false;
    PsiRecordComponent[] components = recordClass.getRecordComponents();
    if (components.length > fields.length) {
      buffer.append(StreamEx.of(components)
                      .map(JavaPsiRecordUtil::getFieldForComponent)
                      .mapToInt(f -> ArrayUtil.indexOf(fields, f))
                      .mapToObj(idx -> idx >= 0 ? parameters[idx].getName() : "")
                      .joining(",", "this(", ")"));
      appendSemicolon(buffer);
      return true;
    }
    return false;
  }

  @Override
  public void appendSemicolon(@NotNull StringBuilder buffer) {
    buffer.append(";");
  }
}
