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
package org.jetbrains.jps.model.java.compiler;

import com.intellij.util.xmlb.annotations.MapAnnotation;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsJavaCompilerOptions {
  public boolean PREFER_TARGET_JDK_COMPILER = true;
  public boolean DEBUGGING_INFO = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean DEPRECATION = true;
  public String ADDITIONAL_OPTIONS_STRING = "";
  @MapAnnotation(surroundWithTag = false, entryTagName = "module", keyAttributeName = "name", valueAttributeName = "options")
  public Map<String, String> ADDITIONAL_OPTIONS_OVERRIDE = new HashMap<>();
  public int MAXIMUM_HEAP_SIZE = 128;
}
