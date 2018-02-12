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
package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public class IgnoredFileIndexImpl implements IgnoredFileIndex {
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();

  public IgnoredFileIndexImpl(JpsModel model) {
    myIgnoredPatterns.setIgnoreMasks(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }

  @Override
  public boolean isIgnored(String fileName) {
    return myIgnoredPatterns.isIgnored(fileName);
  }
}
