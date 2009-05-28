/*
 * User: anna
 * Date: 28-May-2009
 */
package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface InspectionToolsFactory {
  ExtensionPointName<InspectionToolsFactory> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.inspectionToolsFactory");

  InspectionProfileEntry[] createTools();
}