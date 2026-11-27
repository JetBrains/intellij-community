// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryConstructorFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryConstructorInspection()));

  @Test
  public void testRemoveDefaultConstructor() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           """
             public class Foo {
                 public Foo/**/() {}
             }
             """,
           """
             public class Foo {
             }
             """
    );
  }

  @Test
  public void testRemoveConstructorCallingSuper() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           """
             public class Foo {
                 public Foo/**/() {
                   super();
                 }
             }
             """,
           """
             public class Foo {
             }
             """
    );
  }

  @Test
  public void testRemoveAnnotatedConstructor() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
           """
             public class Foo {
                 @Deprecated
                 public Foo/**/() {}
             }
             """,
           """
             public class Foo {
             }
             """
    );
  }

  @Test
  public void testDoNotFixConstructorWithParameter() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/(int i) {}
                                 }
                                 """
    );
  }

  @Test
  public void testDoNotFixPrivateConstructor() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     private Foo/**/() {}
                                 }
                                 """
    );
  }

  @Test
  public void testDoNotFixConstructorAmongOthers() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/() {}
                                     public Foo(int i) {}
                                 }
                                 """
    );
  }

  @Test
  public void testDoNotFixNotEmptyConstructor() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/() {
                                       System.out.println("foo");
                                     }
                                 }
                                 """
    );
  }

  @Test
  public void testDoNotFixNotEmptySuperCall() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo extends java.util.Date {
                                     public Foo/**/() {
                                       super(1, 1, 1);
                                     }
                                 }
                                 """
    );
  }
}
