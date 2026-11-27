package com.siyeh.ig.fixes.classlayout;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.classlayout.ClassInitializerInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ClassInitializerFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ClassInitializerInspection()), "classlayout/class_initializer",
                        InspectionGadgetsBundle.message("class.initializer.move.code.to.constructor.quickfix"));

  @Test
  public void testNoConstructor() { ig.get().doTest(); }
  @Test
  public void testChainedConstructor() { ig.get().doTest(); }
  @Test
  public void testNonStaticMemberUsed() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("change.modifier.quickfix", "static")); }
}
