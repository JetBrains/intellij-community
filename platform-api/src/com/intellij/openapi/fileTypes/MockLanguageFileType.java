/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MockLanguageFileType extends LanguageFileType {
  public static LanguageFileType INSTANCE = new MockLanguageFileType();

  private MockLanguageFileType() {
    super(Language.ANY);
  }

  @NotNull
  public String getName() {
    return "Mock";
  }

  @NotNull
  public String getDescription() {
    return "Mock";
  }

  @NotNull
  public String getDefaultExtension() {
    return ".mockExtensionThatProbablyWon'tEverExist";
  }

  public Icon getIcon() {
    return null;
  }
}
