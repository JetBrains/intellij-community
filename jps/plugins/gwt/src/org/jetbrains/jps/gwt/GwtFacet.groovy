package org.jetbrains.jps.gwt

import org.jetbrains.jps.Module
import org.jetbrains.jps.idea.Facet

/**
 * @author nik
 */
class GwtFacet extends Facet {
  Module module
  String compilerMaxHeapSize = "128"
  String scriptOutputStyle = "DETAILED"
  String additionalCompilerParameters = ""
  String sdkPath
  String tempOutputDir
}
