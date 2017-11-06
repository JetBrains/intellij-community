// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard.importSources;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author nik
 */
public class JavaSourceRootDetectionUtilTest {
  @Test
  public void simple() {
    assertPackageDetected("package p; \n" +
                          "class A {}", "p");
    assertPackageDetected("package p; \n" +
                          "import java.util.Set;" +
                          "class A {}", "p");
    assertPackageDetected("class A {}", "");
    assertPackageDetected("import java.util.*;\n" +
                          "class A {}", "");
    assertPackageDetected("invalid", null);
  }

  @Test
  public void comments() {
    assertPackageDetected("/* aa */ package p;", "p");
    assertPackageDetected("// aa\n" +
                          "package p;", "p");
    assertPackageDetected("/* a */ /* b */ package p;", "p");
    assertPackageDetected("package /* a */ p;", "p");
    assertPackageDetected("package p /* a */;", "p");
  }

  @Test
  public void qualified() {
    assertPackageDetected("package p.q;", "p.q");
    assertPackageDetected("package p . q;", "p.q");
    assertPackageDetected("package /* xxx */ p . q;", "p.q");
    assertPackageDetected("package p . /* xxx */ q;", "p.q");
  }

  @Test
  public void validPackageAnnotation() {
    assertPackageDetected("@Deprecated package p.q;", "p.q");
    assertPackageDetected("@java.lang.Deprecated package p.q;", "p.q");
    assertPackageDetected("@Generated(\"text\") package p.q;", "p.q");
    assertPackageDetected("@Generated( (\"text\") ) package p.q;", "p.q");
    assertPackageDetected("@Generated((\"text\"), (\"text\")) package p.q;", "p.q");
    assertPackageDetected("@Generated(\"text\")\n" +
                          "@Deprecated\n" +
                          "package p.q;", "p.q");
    assertPackageDetected("@Deprecated/*aa*/ package p.q;", "p.q");
  }

  @Test
  public void invalidPackageAnnotation() {
    assertPackageDetected("@Deprecatedpackage p.q;", null);
    assertPackageDetected("@javax.annotation.Generated(\"text\" package p.q;", null);
  }

  private static void assertPackageDetected(String text, String packageName) {
    assertEquals(packageName, JavaSourceRootDetectionUtil.getPackageName(text));
  }
}
