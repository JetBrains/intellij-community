package com.intellij.execution.filters;

import com.intellij.execution.ConsoleFolding;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;

import java.util.List;

/**
 * @author peter
 */
public class AuxiliaryCallsFolding extends ConsoleFolding {
  @Override
  public boolean shouldFoldLine(String line) {
    final Trinity<String, String, TextRange> pair = ExceptionFilter.parseExceptionLine(line);
    return pair != null && shouldFold(pair.first);
  }

  private static boolean shouldFold(String className) {
    for (StackFrameFilter provider : StackFrameFilter.EP_NAME.getExtensions()) {
      if (provider.isAuxiliaryFrame(className, "")) {
        return true;
      }
    }
    return false;
  }


  @Override
  public String getPlaceholderText(List<String> lines) {
    return " <" + lines.size() + " internal calls>";
  }
}
