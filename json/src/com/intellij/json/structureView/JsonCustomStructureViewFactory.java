// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.json.psi.JsonFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used for customization of default structure view for json files. Note that in case several extensions for current EP are registered,
 * the behaviour is undefined and can be changed by `order` option in plugin.xml.
 * Therefore, there is no guarantee that the expected builder will be returned every time.
 */
@ApiStatus.Internal
public interface JsonCustomStructureViewFactory {
  ExtensionPointName<JsonCustomStructureViewFactory> EP_NAME = ExtensionPointName.create("com.intellij.json.customStructureViewFactory");

  /**
   * First not-null builder received from all registered extensions will be used for building structure view.
   * If extensions list is empty, default implementation is used.
   *
   * @return a structure view builder for the given Json file or null if the file doesn't need customized structure view.
   */
  @Nullable
  StructureViewBuilder getStructureViewBuilder(@NotNull final JsonFile jsonFile);
}
