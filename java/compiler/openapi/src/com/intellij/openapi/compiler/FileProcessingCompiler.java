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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base interface describing shared functionality for various types of file processing compilers which participate in the build process
 * and should be executed inside the IDE process.
 *
 * @deprecated starting from IDEA 15 compilers need to be executed inside a separate (external) build process, see
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/reference_guide/frameworks_and_external_apis/external_builder_api.html">this guide</a>
 * for details. Implementations of this class aren't used by the IDE (except those which implement {@link Validator} or {@link SourceInstrumentingCompiler}).
 */
@Deprecated
public interface FileProcessingCompiler extends Compiler, ValidityStateFactory {
  /**
   * Describes a processing unit for this compiler - a virtual file with associated state.
   */
  interface ProcessingItem {
    /**
     * A utility constant used to return empty arrays of ProcessingItem objects
     */
    ProcessingItem[] EMPTY_ARRAY = new ProcessingItem[0];

    /**
     * Returns the file to be processed.
     *
     * @return a file to be processed; cannot be null
     */
    @NotNull
    VirtualFile getFile();

    /**
     * @return an object describing dependencies of the instrumented file (can be null).
     *         For example, if the file "A" should be processed whenever file "B" or file "C" is changed, the ValidityState object can
     *         be composed of a pair [timestamp("B"), timestamp("C")]. Thus, whenever a timestamp of any of these files is changed,
     *         the current ValidityState won't be equal to the stored ValidityState and the item will be picked up by the make for recompilation.
     */
    @Nullable
    ValidityState getValidityState();
  }

  /**
   * Returns the items which will be processed in the current compile operation.
   * The method is called before the call to {@link #process} method
   *
   * @param context the current compilation context.
   * @return a non-null array of all items that potentially can be processed at the moment of method call. Even if
   *         the file is not changed, it should be returned if it _can_ be processed by the compiler implementing the interface.
   */
  ProcessingItem @NotNull [] getProcessingItems(@NotNull CompileContext context);

  /**
   * Compiles the specified items.
   *
   * @param context the current compilation context.
   * @param items items to be processed, selected by make subsystem. The items are selected from the list returned by the
   *              {@link #getProcessingItems} method.
   * @return successfully processed items.
   */
  ProcessingItem[] process(@NotNull CompileContext context, ProcessingItem @NotNull [] items);

}
