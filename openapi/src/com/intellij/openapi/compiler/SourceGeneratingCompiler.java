/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

/**
 * A tag interface indicating that generating compiler will actually generate java sources.
 * This affects the order of compiler calls
 * The sequence in which compilers are called:
 * SourceGeneratingCompiler -> SourceInstrumentingCompiler -> TranslatingCompiler ->  ClassInstrumentingCompiler -> ClassPostProcessingCompiler -> Validator
 */
public interface SourceGeneratingCompiler extends GeneratingCompiler {
}
