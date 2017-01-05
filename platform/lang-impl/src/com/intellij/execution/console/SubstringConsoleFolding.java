package com.intellij.execution.console;

import com.intellij.execution.ConsoleFolding;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class SubstringConsoleFolding extends ConsoleFolding {
  private final ConsoleFoldingSettings mySettings;

  public SubstringConsoleFolding(ConsoleFoldingSettings settings) {
    mySettings = settings;
  }

  @Override
  public boolean shouldFoldLine(@NotNull String line) {
    return mySettings.shouldFoldLine(line);
  }

  @Override
  public String getPlaceholderText(@NotNull List<String> lines) {
    return " <" + lines.size() + " internal calls>";
  }
}
