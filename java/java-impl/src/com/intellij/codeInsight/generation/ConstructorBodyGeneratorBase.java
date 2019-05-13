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

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class ConstructorBodyGeneratorBase implements ConstructorBodyGenerator {
  public void generateFieldInitialization(@NotNull StringBuilder buffer,
                                          @NotNull PsiField[] fields,
                                          @NotNull PsiParameter[] parameters,
                                          @NotNull Collection<String> existingNames) {
    for (int i = 0, length = fields.length; i < length; i++) {
      String fieldName = fields[i].getName();
      String paramName = parameters[i].getName();
      if (existingNames.contains(fieldName)) {
        buffer.append("this.");
      }
      buffer.append(fieldName);
      buffer.append("=");
      buffer.append(paramName);
      appendSemicolon(buffer);
      buffer.append("\n");
    }
  }

  @Override
  public void generateFieldInitialization(@NotNull StringBuilder buffer, @NotNull PsiField[] fields, @NotNull PsiParameter[] parameters) {}

  protected void appendSemicolon(@NotNull StringBuilder buffer) {}

  @Override
  public void generateSuperCallIfNeeded(@NotNull StringBuilder buffer, @NotNull PsiParameter[] parameters) {
    if (parameters.length > 0) {
      buffer.append("super(");
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter param = parameters[j];
        buffer.append(param.getName());
        if (j < parameters.length - 1) buffer.append(",");
      }
      buffer.append(")");
      appendSemicolon(buffer);
      buffer.append("\n");
    }
  }

  @Override
  public StringBuilder start(StringBuilder buffer, @NotNull String name, @NotNull PsiParameter[] parameters) {
    buffer.append("public ").append(name).append("(");
    for (PsiParameter parameter : parameters) {
      buffer.append(parameter.getType().getPresentableText()).append(' ').append(parameter.getName()).append(',');
    }
    if (parameters.length > 0) {
      buffer.delete(buffer.length() - 1, buffer.length());
    }
    buffer.append("){\n");
    return buffer;
  }
}
