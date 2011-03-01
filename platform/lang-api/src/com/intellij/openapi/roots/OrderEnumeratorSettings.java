/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

public class OrderEnumeratorSettings {
  public boolean productionOnly;
  public boolean compileOnly;
  public boolean runtimeOnly;
  public boolean withoutJdk;
  public boolean withoutLibraries;
  public boolean withoutDepModules;
  public boolean withoutModuleSourceEntries;
  public boolean recursively;
  public boolean recursivelyExportedOnly;
  public boolean exportedOnly;

  public int getFlags() {
    int flags = 0;
    if (productionOnly) flags |= 1;
    flags <<= 1;
    if (compileOnly) flags |= 1;
    flags <<= 1;
    if (runtimeOnly) flags |= 1;
    flags <<= 1;
    if (withoutJdk) flags |= 1;
    flags <<= 1;
    if (withoutLibraries) flags |= 1;
    flags <<= 1;
    if (withoutDepModules) flags |= 1;
    flags <<= 1;
    if (withoutModuleSourceEntries) flags |= 1;
    flags <<= 1;
    if (recursively) flags |= 1;
    flags <<= 1;
    if (recursivelyExportedOnly) flags |= 1;
    flags <<= 1;
    if (exportedOnly) flags |= 1;
    return flags;
  }
}
