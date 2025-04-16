// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.frontend.codeInsight.highlighting;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.pages.AbstractBasicJavaColorSettingsPage;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JavaColorSettingsPageBase extends AbstractBasicJavaColorSettingsPage {

  @Override
  public final Icon getIcon() {
    return JavaFileType.INSTANCE.getIcon();
  }

  @Override
  public final @NotNull SyntaxHighlighter getHighlighter() {
    return new JavaFileHighlighter(LanguageLevel.HIGHEST);
  }
}
