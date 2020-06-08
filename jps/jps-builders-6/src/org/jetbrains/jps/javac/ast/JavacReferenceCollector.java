// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac.ast;

import com.intellij.util.Consumer;
import org.jetbrains.jps.javac.ast.api.JavacFileData;

import javax.tools.*;

/**
 * Code here should not depend on any javac private API located in tools.jar if no JavacFileReferencesRegistrar-s will be run.
 * A workaround to allow run standalone jps with improperly configured classloader without NoClassDefFoundError (e.g: IDEA-162877)
 */
public final class JavacReferenceCollector {
  public static void installOn(JavaCompiler.CompilationTask task,
                               Consumer<? super JavacFileData> fileDataConsumer) {
    JavacReferenceCollectorListener.installOn(task, fileDataConsumer);
  }
}
