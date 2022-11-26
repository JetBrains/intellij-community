/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.execution;

import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.history.ImportedToGeneralTestEventsConverter;
import com.intellij.openapi.util.Disposer;

import java.io.StringReader;
import java.util.List;

public class AntImportTest extends BaseSMTRunnerTestCase {
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRootNode = new SMTestProxy.SMRootTestProxy();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(getProject(), myRootNode, "SMTestFramework");

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myEventsProcessor.onFinishTesting();
      Disposer.dispose(myEventsProcessor);
      Disposer.dispose(myRootNode);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myRootNode = null;
      myEventsProcessor = null;
      super.tearDown();
    }
  }

  public void testAntFormatTest() throws Exception {
    String fileText = """
      <?xml version="1.0" encoding="UTF-8" ?>
      <testsuites>
        <testsuite errors="0" failures="0" hostname="ignore" id="1" name="MyTest" package="a" skipped="0" tests="3" time="0.062" timestamp="2016-10-10T19:25:11">
            <testcase classname="a.MyTest" name="testA1" time="0.002" />
            <testcase classname="a.MyTest" name="testA2" time="0.0" />
            <testcase classname="a.MyTest" name="testA3" time="0.0" />
            <system-out><![CDATA[]]></system-out>
            <system-err><![CDATA[]]></system-err>
        </testsuite>
      </testsuites>
      """;

    ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(fileText), myEventsProcessor);

    final List<? extends SMTestProxy> children = myRootNode.getChildren();
    assertEquals(1, children.size());
    final SMTestProxy suite = children.get(0);
    assertEquals("MyTest", suite.getName());
    final List<? extends SMTestProxy> tests = suite.getChildren();
    assertEquals(3, tests.size());
    assertEquals("testA1", tests.get(0).getName());
  }
}
