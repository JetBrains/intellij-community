// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Pluggable part of Generate Constructor action in java
 * 
 * Was introduced mainly to skip semicolons in generated groovy constructors. 
*/
@ApiStatus.Internal
public interface ConstructorBodyGenerator {
  LanguageExtension<ConstructorBodyGenerator> INSTANCE = new LanguageExtension<>("com.intellij.constructorBodyGenerator");

  default void generateFieldInitialization(@NotNull StringBuilder buffer,
                                           PsiField @NotNull [] fields,
                                           PsiParameter @NotNull [] parameters,
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

  default void appendSemicolon(@NotNull StringBuilder buffer) {}

  default void generateSuperCallIfNeeded(@NotNull StringBuilder buffer, PsiParameter @NotNull [] parameters) {
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

  default StringBuilder start(StringBuilder buffer, @NotNull String name, PsiParameter @NotNull [] parameters) {
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

  default void finish(StringBuilder builder) {
    builder.append('}');
  }
}
