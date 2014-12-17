/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * Indexes the output roots of individual build targets.
 *
 * @author nik
 */
public interface TargetOutputIndex {
  /**
   * Returns the list of targets that contain the specified output file in their output roots.
   *
   * @param file a build output file.
   * @return a collection of targets to the output roots of which this file belongs, or an empty collection
   * if no such targets exist.
   */
  Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file);
}
