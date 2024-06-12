// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import org.jdom.Element;

/**
 * Modifies inspectionResult after jdom is created for problem descriptor.
 */
public interface ExportedInspectionsResultModifier {
  void modifyResult(Element inspectionResult);
}
