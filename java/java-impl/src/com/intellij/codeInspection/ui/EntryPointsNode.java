/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author max
 */
public class EntryPointsNode extends InspectionNode {
  private static final Icon ENTRY_POINTS = IconLoader.getIcon("/nodes/entryPoints.png");
  public EntryPointsNode(UnusedDeclarationInspection tool) {
    super(new DummyEntryPointsTool(tool));
    getTool().updateContent();
  }

  public Icon getIcon(boolean expanded) {
    return ENTRY_POINTS;
  }
}
