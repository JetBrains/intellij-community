// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static com.intellij.codeInspection.java19api.ModuleNode.DependencyType.STATIC;
import static com.intellij.codeInspection.java19api.ModuleNode.DependencyType.TRANSITIVE;
import static com.intellij.psi.PsiJavaModule.JAVA_BASE;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

record ModuleInfo(@NotNull PsiDirectory rootDir, @NotNull ModuleNode node) {
  boolean fileAlreadyExists() {
    return ReadAction.compute(() -> StreamEx.of(rootDir().getChildren())
      .select(PsiFile.class)
      .map(PsiFileSystemItem::getName)
      .anyMatch(MODULE_INFO_FILE::equals));
  }

  @NotNull
  CharSequence createModuleText() {
    CharSequence requires = requiresText();
    CharSequence exports = exportsText();

    return new StringBuilder().append(PsiKeyword.MODULE).append(" ").append(node().getName()).append(" {\n")
      .append(requires)
      .append((!requires.isEmpty() && !exports.isEmpty()) ? "\n" : "")
      .append(exports)
      .append("}");
  }

  @NotNull
  private CharSequence requiresText() {
    StringBuilder text = new StringBuilder();
    for (Map.Entry<ModuleNode, Set<ModuleNode.DependencyType>> dependency : node().getDependencies().entrySet()) {
      if(dependency.getValue() == null) continue;
      final String dependencyName = dependency.getKey().getName();
      if (JAVA_BASE.equals(dependencyName)) continue;
      boolean isBadSyntax = ContainerUtil.or(dependencyName.split("\\."),
                                             part -> JavaLexer.isKeyword(part, LanguageLevel.JDK_1_9));

      text.append(isBadSyntax ? "// " : " ").append(PsiKeyword.REQUIRES).append(' ');
      if(dependency.getValue().contains(STATIC)) {
        text.append(PsiKeyword.STATIC).append(' ');
      }
      if(dependency.getValue().contains(TRANSITIVE)) {
        text.append(PsiKeyword.TRANSITIVE).append(' ');
      }
      text.append(dependencyName).append(";\n");
    }
    return text;
  }

  @NotNull
  private CharSequence exportsText() {
    StringBuilder text = new StringBuilder();
    for (String packageName : node().getExports()) {
      text.append(PsiKeyword.EXPORTS).append(' ').append(packageName).append(";\n");
    }
    return text;
  }
}
