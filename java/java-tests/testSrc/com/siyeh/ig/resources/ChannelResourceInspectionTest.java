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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ChannelResourceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ChannelResourceInspection());

  @BeforeEach
  void setUp() {
    inspectionFixture.get().addEnvironmentClass("package java.nio.channels;" +
                        "public interface Channel extends java.io.Closeable {}");
    inspectionFixture.get().addEnvironmentClass("package java.nio.channels;" +
                        "public interface FileChannel extends Channel {}");
    inspectionFixture.get().addEnvironmentClass("package java.net;" +
                        "import java.nio.channels.FileChannel;" +
                        "public class SocketInputStream implements java.io.Closeable {" +
                        "  public FileChannel getChannel() {" +
                        "    return null;" +
                        "  }" +
                        "  public void close() throws IOException {}" +
                        "}");
  }

  @Test
  public void testFactoryClosed() {
    inspectionFixture.get().doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m() throws IOException {" +
           "    SocketInputStream ss = new SocketInputStream();" +
           "    try {" +
           "      ss.getChannel();" +
           "    } finally {" +
           "      ss.close();" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m(SocketInputStream ss) throws IOException {" +
           "    ss./*'FileChannel' should be opened in front of a 'try' block and closed in the corresponding 'finally' block*/getChannel/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testChannelClosed() {
    inspectionFixture.get().doTest("import java.net.*;" +
           "import java.io.*;" +
           "class X {" +
           "  void m(SocketInputStream ss) throws IOException {" +
           "    final Closeable channel = ss.getChannel();" +
           "    try {" +
           "      System.out.println();" +
           "    } finally {" +
           "      channel.close();" +
           "    }" +
           "  }" +
           "}");
  }
}