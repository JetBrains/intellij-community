/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;


/**
 * A tag interface indicating that the instrumenting compiler will actually instrument java classes.
 * This affects the order of compiler calls:
 * The sequence in which compilers are called:
 * SourceGeneratingCompiler -> SourceInstrumentingCompiler -> TranslatingCompiler ->  ClassInstrumentingCompiler -> ClassPostProcessingCompiler -> Validator
 */
public interface ClassInstrumentingCompiler extends FileProcessingCompiler {
}
