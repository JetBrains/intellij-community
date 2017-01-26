/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

public interface SchemesModel<T extends Scheme> {
  boolean supportsProjectSchemes();
  
  boolean canDuplicateScheme(@NotNull T scheme );
  
  boolean canResetScheme(@NotNull T scheme);
  
  boolean canDeleteScheme(@NotNull T scheme);
  
  boolean isProjectScheme(@NotNull T scheme);
  
  boolean canRenameScheme(@NotNull T scheme);
  
  boolean nameExists(@NotNull String name);

  boolean differsFromDefault(@NotNull T scheme);
}
