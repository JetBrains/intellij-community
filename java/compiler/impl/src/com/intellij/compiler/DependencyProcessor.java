/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.make.CachingSearcher;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 19, 2008
 */
public interface DependencyProcessor {
  ExtensionPointName<DependencyProcessor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.compiler.makeDependencyProcessor");
  
  void processDependencies(CompileContext context, int classQualifiedName, CachingSearcher searcher) throws CacheCorruptedException;
}
