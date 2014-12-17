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

/**
 * The category of a module-level builder. Determines the order of executing builders when compiling a module.
 *
 * @author Eugene Zhuravlev
 * @since  9/17/11
 * @see ModuleLevelBuilder#getCategory()
 */
public enum BuilderCategory {
  INITIAL,
  SOURCE_GENERATOR,
  SOURCE_INSTRUMENTER,
  SOURCE_PROCESSOR,
  TRANSLATOR,
  OVERWRITING_TRANSLATOR,
  CLASS_INSTRUMENTER,
  CLASS_POST_PROCESSOR,
}
