package com.intellij.diagnostic.logging;

/**
 * @author yole
 */
public interface LogConsole {
  LogContentPreprocessor getContentPreprocessor();

  void setContentPreprocessor(LogContentPreprocessor contentPreprocessor);

  void setFilterModel(LogFilterModel model);

  LogFilterModel getFilterModel();
}
