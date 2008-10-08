package com.intellij.diagnostic.logging;

import java.util.List;

/**
 * @author yole
 */
public interface LogContentPreprocessor {
  List<LogFragment> parseLogLine(String text);
}
