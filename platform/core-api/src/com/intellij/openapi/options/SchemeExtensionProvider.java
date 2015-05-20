/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

/**
 * A scheme processor can implement this interface to provide a file extension different from default .xml.
 * @see SchemeProcessor
 */
public interface SchemeExtensionProvider {

  /**
   * @return The scheme file extension <b>with e leading dot</b>, for example ".ext".
   */
  @NotNull
  String getSchemeExtension();

  /**
   * @return True if the upgrade from the old default .xml extension is needed.
   */
  boolean isUpgradeNeeded();
}
