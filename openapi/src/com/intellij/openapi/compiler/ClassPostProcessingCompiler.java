/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;


/**
 * A tag interface indicating that the file processing compiler will actually take java classes and perform some activities on them.
 * This affects the order of compiler calls:
 * The sequence in which compilers are called:
 * SourceGeneratingCompiler -> SourceInstrumentingCompiler -> TranslatingCompiler ->  ClassInstrumentingCompiler -> ClassPostProcessingCompiler-> Validator
 */
public interface ClassPostProcessingCompiler extends FileProcessingCompiler {
}
