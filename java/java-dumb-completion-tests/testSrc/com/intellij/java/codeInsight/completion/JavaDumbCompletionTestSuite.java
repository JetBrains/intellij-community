// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplateProvider;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the {@link IndexingModeTemplate} classes of the Java completion tests in the dumb indexing modes.
 * A normal run of these classes runs them only in the smart mode.
 * <p>
 * The suite runs only in the "Java Dumb Completion Tests" build, as {@link JavaCompletionTestSuite} does.
 * To run it locally, add the system property {@code -Dintellij.test.indexing.modes=dumb}.
 * The same property runs one class in the dumb modes.
 */
@Suite(failIfNoTests = false)
@SelectPackages("com.intellij.java.codeInsight")
@IncludeTags(IndexingModeTemplate.TAG)
@ConfigurationParameter(key = IndexingModeTemplateProvider.MODES_PARAMETER, value = "dumb")
@ConfigurationParameter(key = IndexingModeTemplateProvider.BUILD_PARAMETER, value = "Java Dumb Completion Tests")
public final class JavaDumbCompletionTestSuite {
}
