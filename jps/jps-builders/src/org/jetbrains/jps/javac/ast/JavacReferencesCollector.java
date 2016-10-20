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

import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.service.JpsServiceManager;

import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Code here should not depend on any javac private API located in tools.jar if no JavacFileReferencesRegistrar-s will be run.
 * A workaround to allow run standalone jps with improperly configured classloader without NoClassDefFoundError (e.g: IDEA-162877)
 */
public class JavacReferencesCollector {
  public static void installOn(JavaCompiler.CompilationTask task) {
    List<JavacFileReferencesRegistrar> fullASTListeners = new SmartList<JavacFileReferencesRegistrar>();
    List<JavacFileReferencesRegistrar> onlyImportsListeners = new SmartList<JavacFileReferencesRegistrar>();
    for (JavacFileReferencesRegistrar listener : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
      if (!listener.initialize()) {
        continue;
      }
      (listener.onlyImports() ? onlyImportsListeners : fullASTListeners).add(listener);
    }

    final JavacFileReferencesRegistrar[] fullASTListenerArray = fullASTListeners.toArray(new JavacFileReferencesRegistrar[fullASTListeners.size()]);
    final JavacFileReferencesRegistrar[] onlyImportsListenerArray = onlyImportsListeners.toArray(new JavacFileReferencesRegistrar[onlyImportsListeners.size()]);
    if (fullASTListenerArray.length == 0 && onlyImportsListenerArray.length == 0) {
      return;
    }

    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "addTaskListener", TaskListener.class); // jdk >= 8
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(fullASTListenerArray, onlyImportsListenerArray);
    if (addTaskMethod != null) {
      try {
        addTaskMethod.invoke(task, taskListener);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    } else {
      // jdk 6-7
      javacTask.setTaskListener(taskListener);
    }
  }
}
