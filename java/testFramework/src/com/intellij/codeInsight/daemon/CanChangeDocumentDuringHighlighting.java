// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets the highlighting of a test method, or of each test method of a class, change the document.
 * {@link DaemonAnalyzerFixture}, {@link LightDaemonAnalyzerTestCase} and {@link DaemonAnalyzerTestCase} read it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CanChangeDocumentDuringHighlighting {
}
