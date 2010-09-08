package org.jetbrains.jps;

/**
 * Expands IDEA macros
 *
 * @deprecated use {@link org.jetbrains.jps.MacroExpander} instead
 */
public interface MacroExpansion extends MacroExpander {
  /**
   * @deprecated use {@link #expandMacros(String)} instead
   */
  String expandProjectMacro(String path);
}
