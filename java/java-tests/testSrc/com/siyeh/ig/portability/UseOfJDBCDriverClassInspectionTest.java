// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.portability;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class UseOfJDBCDriverClassInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new UseOfJDBCDriverClassInspection(), getEnvironmentClasses());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("import com.pany.*;" +
           "class X {" +
           "  /*Use of concrete JDBC driver class 'ConcreteDriver' is non-portable*/ConcreteDriver/**/ m() {" +
           "    return new /*Use of concrete JDBC driver class 'ConcreteDriver' is non-portable*/ConcreteDriver/**/();" +
           "  }" +
           "}");
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package com.pany;" +
      "import java.sql.*;" +
      "import java.util.Properties;" +
      "import java.util.logging.Logger;" +
      "public class ConcreteDriver implements Driver {" +
      "  @Overridepublic Connection connect(String url, Properties info) throws SQLException {" +
      "    return null;" +
      "  }" +
      "  @Overridepublic boolean acceptsURL(String url) throws SQLException {" +
      "    return false;" +
      "  }" +
      "  @Overridepublic DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {" +
      "    return new DriverPropertyInfo[0];" +
      "  }" +
      "  @Overridepublic int getMajorVersion() {" +
      "    return 0;" +
      "  }" +
      "  @Overridepublic int getMinorVersion() {" +
      "    return 0;" +
      "  }" +
      "  @Overridepublic boolean jdbcCompliant() {" +
      "    return false;" +
      "  }" +
      "  @Overridepublic Logger getParentLogger() throws SQLFeatureNotSupportedException {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }
}