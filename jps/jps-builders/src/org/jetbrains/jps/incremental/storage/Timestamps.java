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
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public interface Timestamps {
  void force();

  void saveStamp(File file, BuildTarget<?> buildTarget, long timestamp) throws IOException;

  void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException;

  void clean() throws IOException;

  long getStamp(File file, BuildTarget<?> target) throws IOException;
}
