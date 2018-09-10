/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.execution.testframework.sm.runner.history.ImportTestOutputExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.Reader;

public class AntTestContentHandler extends DefaultHandler {
  public static class AntTestOutputExtension implements ImportTestOutputExtension {

    @Nullable
    @Override
    public DefaultHandler createHandler(final Reader reader, GeneralTestEventsProcessor processor) throws IOException {
      final String[] rooName = new String[]{null};
      NanoXmlUtil.parse(reader, new NanoXmlUtil.IXMLBuilderAdapter() {
        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          rooName[0] = name;
        }
      });
      return TESTSUITES.equals(rooName[0]) ? new AntTestContentHandler(processor)
                                           : null;
    }
  }

  private static final String TESTSUITES = "testsuites";
  private static final String TESTSUITE = "testsuite";
  private static final String TESTCASE = "testcase";
  private static final String NAME = "name";
  private static final String PACKAGE = "package";
  private static final String CLASSNAME = "classname";
  private static final String DURATION = "time";
  private static final String ERROR = "error";
  private static final String FAILURE = "failure";
  private static final String OUT = "system-out";
  private static final String ERR = "system-err";

  private final GeneralTestEventsProcessor myProcessor;
  private final Stack<String> mySuites = new Stack<>();
  private String myCurrentTest;
  private String myDuration;
  private String myStatus;
  private final StringBuilder currentValue = new StringBuilder();
  private boolean myErrorOutput = false;

  public AntTestContentHandler(GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (TESTSUITE.equals(qName)) {
      final String suiteName = StringUtil.unescapeXml(attributes.getValue(NAME));
      final String packageName = StringUtil.unescapeXml(attributes.getValue(PACKAGE));
      myProcessor
        .onSuiteStarted(new TestSuiteStartedEvent(suiteName, "java:suite://" + StringUtil.getQualifiedName(packageName, suiteName)));
      mySuites.push(suiteName);
    }
    else if (TESTCASE.equals(qName)) {
      final String name = StringUtil.unescapeXml(attributes.getValue(NAME));
      myCurrentTest = name;
      myStatus = null;
      myDuration = attributes.getValue(DURATION);
      String classname = StringUtil.unescapeXml(attributes.getValue(CLASSNAME));
      String location = StringUtil.isEmpty(classname) ? name : classname + "/" + name;
      final TestStartedEvent startedEvent = new TestStartedEvent(name, "java:test://" + location);
      myProcessor.onTestStarted(startedEvent);
    }
    else if (ERR.equals(qName)) {
      myErrorOutput = true;
    }
    else if (FAILURE.equals(qName)) {
      myStatus = TestResultsXmlFormatter.STATUS_FAILED;
    }
    else if (ERROR.equals(qName)) {
      myStatus = TestResultsXmlFormatter.STATUS_ERROR;
    }
    currentValue.setLength(0);
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    currentValue.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    final String currentText = StringUtil.unescapeXml(currentValue.toString());
    currentValue.setLength(0);
    if (TESTSUITE.equals(qName)) {
      myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(mySuites.pop()));
    }
    else if (TESTCASE.equals(qName)) {
      if (myStatus != null) {
        myProcessor.onTestFailure(
          new TestFailedEvent(myCurrentTest, "", currentText, myStatus.equals(TestResultsXmlFormatter.STATUS_ERROR), null, null));
      }
      long time;
      try {
        time = (long)(1000 * Float.parseFloat(myDuration));
      }
      catch (NumberFormatException e) {
        time = -1;
      }
      myProcessor.onTestFinished(new TestFinishedEvent(myCurrentTest, time));
      myCurrentTest = null;
    }
    else if ((ERR.equals(qName) || OUT.equals(qName)) && !StringUtil.isEmpty(currentText)) {
      if (myCurrentTest != null) {
        myProcessor.onTestOutput(new TestOutputEvent(myCurrentTest, currentText, !myErrorOutput));
      }
      else {
        myProcessor.onUncapturedOutput(currentText, myErrorOutput ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
      }
    }
  }
}
