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
package org.jetbrains.jps.javac;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.Collection;

/**
* @author Eugene Zhuravlev
*/
public interface DiagnosticOutputConsumer extends DiagnosticListener<JavaFileObject> {
  void outputLineAvailable(String line);
  void registerImports(String className, Collection<String> imports, Collection<String> staticImports);
  void javaFileLoaded(File file);
  void customOutputData(String pluginId, String dataName, byte[] data);
}
