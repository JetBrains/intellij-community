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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettingsBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.util.Chunk;
import org.jetbrains.jps.model.java.compiler.EclipseCompilerOptions;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/27/12
 */
public class EclipseSettingsBuilder extends JavacSettingsBuilder {
  public EclipseSettingsBuilder(final EclipseCompilerOptions options) {
    super(options);
  }

  @Override
  public EclipseCompilerOptions getOptions() {
    return (EclipseCompilerOptions)super.getOptions();
  }

  @Override
  public Collection<String> getOptions(Chunk<Module> chunk) {
    final Collection<String> options = super.getOptions(chunk);
    if (getOptions().PROCEED_ON_ERROR) {
      options.add("-proceedOnError");
    }
    return options;
  }
}
