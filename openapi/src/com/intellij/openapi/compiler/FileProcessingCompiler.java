/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * A base interface describing shared functionality for various types of file processing compilers.
 * Actual compiler implementation should implement one of these:
 * @see ClassInstrumentingCompiler
 * @see ClassPostProcessingCompiler
 * @see SourceInstrumentingCompiler
 */
public interface FileProcessingCompiler extends Compiler, ValidityStateFactory {
  /**
   * Describes a processing unit for this compiler - a virtual file with associated state
   */
  interface ProcessingItem {
    /**
     * A utility constant used to return empty arrays of ProcessingItem objects
     */
    ProcessingItem[] EMPTY_ARRAY = new ProcessingItem[0];
    /**
     * @return a file to be processed; cannot be null
     */
    VirtualFile getFile();
    /**
     * @return an object describing dependencies of the instrumented file (can be null).
     * For example, if the file "A" should be processed whenever file "B" or file "C" is changed, the ValidityState object can
     * be composed of a pair [timestamp("B"), timestamp("C")]. Thus, whenever a timestamp of any of these files is changed,
     * the current ValidityState won't be equal to the stored ValidityState and the item will be picked up by the make for recompilation.
     */
    ValidityState getValidityState();
  }

  /**
   * The method is called before the call to {@link #process} method
   * @param context current compilation context
   * @return a non-null array of all items that potantially can be processed at the moment of method call. Even if
   * the file is not changed, it should be returned if it _can_ be processed by the compiler implementing the interface.
   */
  ProcessingItem[] getProcessingItems(CompileContext context);

  /**
   * @param items items to be processed, selected by make subsustem. The items are selected from the list returned by the
   * {@link #getProcessingItems} method.
   * @return successfully processed items
   */
  ProcessingItem[] process(CompileContext context, ProcessingItem[] items);

}
