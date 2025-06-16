// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.JavaTestUtil;
import com.intellij.java.frontback.psi.impl.syntax.JavaSyntaxDefinitionExtension;
import com.intellij.lang.LanguageASTFactory;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.syntax.JavaElementTypeConverterExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.JavaASTFactory;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.platform.backend.workspace.WorkspaceModelTopics;
import org.jetbrains.annotations.NotNull;

public abstract class JavaParsingTestCase extends ParsingTestCase {
  private LanguageLevel myLanguageLevel;

  public JavaParsingTestCase(String dataPath, String fileExt, ParserDefinition... parserDefinitions) {
    super("psi/" + dataPath, fileExt, parserDefinitions);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLanguageLevel = JavaTestUtil.getMaxRegisteredLanguageLevel();
    getProject().registerService(WorkspaceModelTopics.class, new WorkspaceModelTopics());
    getProject().registerService(LanguageLevelProjectExtension.class, new LanguageLevelProjectExtensionImpl(getProject()));
    addExplicitExtension(LanguageASTFactory.INSTANCE, JavaLanguage.INSTANCE, new JavaASTFactory());
    addExplicitExtension(LanguageSyntaxDefinitions.getINSTANCE(), JavaLanguage.INSTANCE, new JavaSyntaxDefinitionExtension());
    addExplicitExtension(ElementTypeConverters.getInstance(), JavaLanguage.INSTANCE, new JavaElementTypeConverterExtension());
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    PsiFile file = super.createFile(name, text);
    file.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
    return file;
  }

  protected void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
  }
}