/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.javac.ast;

import com.intellij.util.Consumer;
import org.jetbrains.jps.javac.ast.api.JavacFileData;

import javax.tools.*;

/**
 * Code here should not depend on any javac private API located in tools.jar if no JavacFileReferencesRegistrar-s will be run.
 * A workaround to allow run standalone jps with improperly configured classloader without NoClassDefFoundError (e.g: IDEA-162877)
 */
public class JavacReferenceCollector {
  public static void installOn(JavaCompiler.CompilationTask task,
                               boolean divideImportRefs,
                               Consumer<JavacFileData> fileDataConsumer) {
    JavacReferenceCollectorListener.installOn(task, divideImportRefs, fileDataConsumer);
  }
}
