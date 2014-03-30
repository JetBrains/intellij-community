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
package com.intellij.openapi.compiler;

import org.jetbrains.annotations.Nullable;


/**
 * A tag interface indicating that the compiler will package the compiled Java classes.
 * This affects the order of compiler calls.
 * The sequence in which compilers are called:
 * SourceGeneratingCompiler -> SourceInstrumentingCompiler -> TranslatingCompiler ->  ClassInstrumentingCompiler -> ClassPostProcessingCompiler -> PackagingCompiler -> Validator
 *
 * @deprecated this interface is part of the obsolete build system which runs as part of the IDE process. Since IDEA 12 plugins need to
 * integrate into 'external build system' instead (http://confluence.jetbrains.com/display/IDEADEV/External+Builder+API+and+Plugins).
 * Since IDEA 13 users cannot switch to the old build system via UI and it will be completely removed in IDEA 14.
 */
public interface PackagingCompiler extends FileProcessingCompiler{
  /**
   * Called when the compiler detects that an item in the output directory is outdated
   * and will be recompiled. Note that this method will be called before, and independently from,
   * subsequent calls to {@link #process}.
   *
   * @param context the current compile context.
   * @param url     the URL of a file in the output directory which will be recompiled.
   * @param state   the validity state of the file specified by <code>url</code>.
   */
  void processOutdatedItem(CompileContext context, String url, @Nullable ValidityState state);
}
