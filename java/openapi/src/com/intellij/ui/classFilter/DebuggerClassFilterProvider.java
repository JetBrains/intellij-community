package com.intellij.ui.classFilter;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 22, 2008
 */
public interface DebuggerClassFilterProvider {
  ExtensionPointName<DebuggerClassFilterProvider> EP_NAME = new ExtensionPointName<DebuggerClassFilterProvider>("com.intellij.debuggerClassFilterProvider");

  List<ClassFilter> getFilters();
}
