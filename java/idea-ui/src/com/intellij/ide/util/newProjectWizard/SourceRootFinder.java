/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;

import java.io.File;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */

/**
 * @deprecated use {@link com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector} instead
 */
@Deprecated
public interface SourceRootFinder {
  ExtensionPointName<SourceRootFinder> EP_NAME = ExtensionPointName.create("com.intellij.sourceRootFinder");

  List<Pair<File, String>> findRoots(File dir);

  String getDescription();

  String getName();
}
