/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.util.registry.Registry;

/**
 * @author egor
 */
public class FileObjectRenderer extends CompoundReferenceRenderer {
  public FileObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "File", null, NodeRendererSettings.createExpressionChildrenRenderer("listFiles()", null));
    setClassName("java.io.File");
    setEnabled(true);
  }

  @Override
  public boolean isEnabled() {
    return Registry.is("debugger.renderers.file");
  }
}
