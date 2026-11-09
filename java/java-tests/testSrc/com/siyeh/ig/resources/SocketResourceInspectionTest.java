/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.resources;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SocketResourceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SocketResourceInspection());

  @Test
  public void testNoCloseNoVar() {
    inspectionFixture.get().doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "       new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/( InetAddress.getLocalHost(), 1);" +
           "    }" +
           "}");
  }

  @Test
  public void testNoClose() {
    inspectionFixture.get().doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        final Socket socket = new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/(InetAddress.getLocalHost(), 1);" +
           "    }" +
           "}");
  }

  @Test
  @SuppressWarnings("EmptyFinallyBlock")
  public void testTryNoClose() {
    inspectionFixture.get().doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        try {" +
           "            final Socket socket = new /*'Socket' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/Socket/**/(InetAddress.getLocalHost(), 1);" +
           "        } finally {" +
           "        }" +
           "    }" +
           "}");
  }

  @Test
  public void testImmediateClose() {
    inspectionFixture.get().doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        final Socket socket = new Socket(InetAddress.getLocalHost(), 1);" +
           "        socket.close();" +
           "    }" +
           "}");
  }

  @Test
  public void testCorrectClose() {
    //noinspection EmptyTryBlock
    inspectionFixture.get().doTest("import java.io.*;" +
           "import java.net.*;" +
           "class X {" +
           "    public static void m() throws IOException {" +
           "        Socket socket = new Socket(InetAddress.getLocalHost(), 1);" +
           "        try {" +
           "        } finally {" +
           "            socket.close();" +
           "        }" +
           "    }" +
           "}");
  }
}
