/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.compiler;

import com.intellij.CommonBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 9, 2005
 */
public class CompilerBundle {
  @NonNls private static final String BUNDLE = "messages.CompilerBundle";

  private CompilerBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }

  public static String jdkHomeNotFoundMessage(final ProjectJdk jdk) {
    return message("javac.error.jdk.home.missing", jdk.getName(), jdk.getHomePath());
  }
}
