// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryConstructorFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryConstructorInspection();
  }

  public void testRemoveDefaultConstructor() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
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

  public void testRemoveConstructorCallingSuper() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
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

  public void testRemoveAnnotatedConstructor() {
    doTest(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
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

  public void testDoNotFixConstructorWithParameter() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/(int i) {}
                                 }
                                 """
    );
  }

  public void testDoNotFixPrivateConstructor() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     private Foo/**/() {}
                                 }
                                 """
    );
  }

  public void testDoNotFixConstructorAmongOthers() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/() {}
                                     public Foo(int i) {}
                                 }
                                 """
    );
  }

  public void testDoNotFixNotEmptyConstructor() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
                               """
                                 public class Foo {
                                     public Foo/**/() {
                                       System.out.println("foo");
                                     }
                                 }
                                 """
    );
  }

  public void testDoNotFixNotEmptySuperCall() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.constructor.remove.quickfix"),
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
