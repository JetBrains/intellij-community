package org.jetbrains.jps;

/**
 * Expands IDEA macros
 */
public interface MacroExpansion {
  String expandProjectMacro(String path);
}
