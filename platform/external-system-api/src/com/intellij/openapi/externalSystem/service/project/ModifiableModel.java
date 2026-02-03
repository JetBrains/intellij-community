// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import org.jetbrains.annotations.ApiStatus;

/**
 * Custom modification model for custom model that isn't stored or indirectly stored in workspace model.
 */
@ApiStatus.Experimental
public interface ModifiableModel {
  /**
   * Applies all modifications from this model into model that is being modified.
   */
  void commit();

  /**
   * Discard all modification and dispose resources from this model.
   */
  void dispose();
}
