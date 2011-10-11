package org.jetbrains.jps;

/**
 * @author nik
 */
public interface MacroExpander {
  String expandMacros(String path);
}
