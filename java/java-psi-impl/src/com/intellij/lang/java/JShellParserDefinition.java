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
package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.java.parser.JShellParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.JShellFileImpl;
import com.intellij.psi.impl.source.tree.IJShellElementType;
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 * Date: 21-Jun-17
 */
public class JShellParserDefinition extends JavaParserDefinition{
  private static final PsiParser PARSER = new PsiParser() {
    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType rootElement, @NotNull PsiBuilder builder) {
      JavaParserUtil.setLanguageLevel(builder, LanguageLevel.HIGHEST);
      final PsiBuilder.Marker r = builder.mark();
      JShellParser.INSTANCE.getFileParser().parse(builder);
      r.done(rootElement);
      return builder.getTreeBuilt();
    }
  };

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new JShellFileImpl(viewProvider);
  }

  @Override
  public IFileElementType getFileNodeType() {
    return JShellElementType.FILE;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type instanceof IJShellElementType) {
      return ((IJShellElementType)type).createPsi(node);
    }
    return super.createElement(node);
  }

  @NotNull
  @Override
  public PsiParser createParser(Project project) {
    return PARSER;
  }
}
