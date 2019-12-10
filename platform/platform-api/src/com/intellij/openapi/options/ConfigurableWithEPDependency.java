// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The interface is used for configurable that depends on some dynamic extension points.
 * If a configurable implements the interface by default the configurable will re-created after adding / removing extensions for the EP.
 * If configurable can handle the EP changes itself the method {{@link #updateOnExtensionChanged}} must be implemented. 
 * 
 * Examples: postfix template configurable. If we have added a plugin with new postfix templates we have to re-create the configurable
 * (but only if the content of the configurable was loaded)
 * 
 * @apiNote configurable must not initialize EP-depend resources in the constructor
 */
@ApiStatus.Experimental
public interface ConfigurableWithEPDependency<T> extends UnnamedConfigurable {

  /**
   * @return EPName that affects the configurable
   */
  @NotNull
  ExtensionPointName<T> getDependency();

  /**
   * The method is called from EDT similar to {{@link #createComponent()}} 
   * @return true if configurable has successfully updated the UI, false if the configurable must be recreated.
   **/
  default boolean updateOnExtensionChanged(@NotNull T e, @NotNull PluginDescriptor pd) {
    return false;
  }
}
