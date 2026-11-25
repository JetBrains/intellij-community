package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;

/**
 * @deprecated Use {@link com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt#lightQuickFixFixture} with a parameterized
 * test method and {@link com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt#lightQuickFixTestFiles} in a JUnit 5 test.
 */
@Deprecated
public abstract class LightIntentionActionTestCase extends LightQuickFixParameterizedTestCase {
}
