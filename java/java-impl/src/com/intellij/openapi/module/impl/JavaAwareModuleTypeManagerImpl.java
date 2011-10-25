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

/*
 * User: anna
 * Date: 18-Feb-2008
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import org.jetbrains.annotations.NonNls;

public class JavaAwareModuleTypeManagerImpl extends ModuleTypeManagerImpl{
  @NonNls private static final String JAVA_MODULE_ID_OLD = "JAVA";

  @Override
  public ModuleType getDefaultModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public ModuleType findByID(final String moduleTypeID) {
    if (moduleTypeID != null) {
      if (JAVA_MODULE_ID_OLD.equals(moduleTypeID)) {
        return StdModuleTypes.JAVA; // for compatibility with the previous ID that Java modules had
      }
    }
    return super.findByID(moduleTypeID);
  }
}