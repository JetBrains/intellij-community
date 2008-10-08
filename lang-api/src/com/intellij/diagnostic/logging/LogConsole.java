package com.intellij.diagnostic.logging;

/**
 * @author yole
 */
public interface LogConsole {
  LogContentPreprocessor getContentPreprocessor();

  void setContentPreprocessor(LogContentPreprocessor contentPreprocessor);

  boolean isShowStandardFilters();

  void setShowStandardFilters(boolean showStandardFilters);
}
