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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;

import java.util.Collection;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/21/12
 */
public interface BuildListener extends EventListener{

  /**
   * Note: when parallel build is on, might be called from several simultaneously running threads
   * @param paths collection of pairs [output root->relative path to generated file]
   */
  void filesGenerated(Collection<Pair<String, String>> paths);

  void filesDeleted(Collection<String> paths);
}
