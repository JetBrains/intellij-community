package com.siyeh.ig.fixes.jdk;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.jdk.ForeachStatementInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ForeachStatementFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ForeachStatementInspection()), "jdk/foreach_statement",
                        InspectionGadgetsBundle.message("extended.for.statement.replace.quickfix"));

  @Test
  public void testBareCollectionLoop() { ig.get().doTest(); }
  @Test
  public void testBoundedTypes() { ig.get().doTest(); }
  @Test
  public void testGenericTypes() { ig.get().doTest(); }
  @Test
  public void testPrecedence() { ig.get().doTest(); }
  @Test
  public void testWildcards() { ig.get().doTest(); }
}