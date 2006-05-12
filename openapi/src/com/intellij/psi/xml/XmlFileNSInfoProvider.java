/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.psi.xml;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Implementations of this interface exposed as ApplicationComponent add default mappings
 * for namespace prefixes to namespaces for any xml file.
 */
public interface XmlFileNSInfoProvider {
  /**
   * Provides information (if any) for default mappings of namespace prefix to namespace identifiers.
   * @param file for which ns mapping information is requested.
   * @return array of namespace prefix to namespace mappings for given file in the format [nsPrefix, namespaceId] or
   * null if the interface implementation does not know about such mapping.
   * Empty nsPrefix is "", nsPrefix, namespaceId should not be null, invalid mapping table is skipped.
   */
  @Nullable String[][] getDefaultNamespaces(@NotNull XmlFile file);
}
