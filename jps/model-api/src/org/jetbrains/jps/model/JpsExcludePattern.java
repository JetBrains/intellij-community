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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * Specifies a pattern for names of files which should be excluded from a module. If name of a file under {@link #getBaseDirUrl() the base directory}
 * matches {@link #getPattern() the pattern} it'll be excluded from the containing module, if name of a directory matches the pattern the directory
 * and all of its contents will be excluded. '?' and '*' wildcards are supported.
 *
 * @author nik
 */
public interface JpsExcludePattern extends JpsElement {
  @NotNull
  String getBaseDirUrl();

  @NotNull
  String getPattern();
}
