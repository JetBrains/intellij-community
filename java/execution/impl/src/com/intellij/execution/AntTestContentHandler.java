// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.execution.testframework.sm.runner.history.ImportTestOutputExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.Reader;

public class AntTestContentHandler extends DefaultHandler {
  public static class AntTestOutputExtension implements ImportTestOutputExtension {

    @Nullable
    @Override
    public DefaultHandler createHandler(@NotNull Reader reader, GeneralTestEventsProcessor processor) {
      final String[] rooName = new String[]{null};
      NanoXmlUtil.parse(reader, new NanoXmlBuilder() {
        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
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
  private static final String SKIPPED = "skipped";
  private static final String IGNORED = "ignored";
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
      String nameValue = attributes.getValue(NAME);
      final String suiteName = nameValue == null ? "" : StringUtil.unescapeXmlEntities(nameValue);
      String packageValue = attributes.getValue(PACKAGE);
      final String packageName = packageValue == null ? null : StringUtil.unescapeXmlEntities(packageValue);
      myProcessor
        .onSuiteStarted(new TestSuiteStartedEvent(suiteName, "java:suite://" + StringUtil.getQualifiedName(packageName,
                                                                                                           StringUtil.notNullize(suiteName))));
      mySuites.push(suiteName);
    }
    else if (TESTCASE.equals(qName)) {
      String nameValue = attributes.getValue(NAME);
      final String name = nameValue == null ? "" : StringUtil.unescapeXmlEntities(nameValue);
      myCurrentTest = name;
      myStatus = null;
      myDuration = attributes.getValue(DURATION);
      String classNameValue = attributes.getValue(CLASSNAME);
      String classname = classNameValue == null ? null : StringUtil.unescapeXmlEntities(classNameValue);
      String location = StringUtil.isEmpty(classname) ? name : classname + "/" + name;
      final TestStartedEvent startedEvent = new TestStartedEvent(name, "java:test://" + location);
      myProcessor.onTestStarted(startedEvent);
    }
    else if (ERR.equals(qName)) {
      myErrorOutput = true;
    }
    else if (SKIPPED.equals(qName)) {
      myStatus = TestResultsXmlFormatter.STATUS_SKIPPED;
    }
    else if (IGNORED.equals(qName)) {
      myStatus = TestResultsXmlFormatter.STATUS_IGNORED;
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
  public void characters(char[] ch, int start, int length) {
    currentValue.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    final String currentText = StringUtil.unescapeXmlEntities(currentValue.toString());
    currentValue.setLength(0);
    if (TESTSUITE.equals(qName)) {
      myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(mySuites.pop()));
    }
    else if (TESTCASE.equals(qName)) {
      if (myStatus != null) {
        switch (myStatus) {
          case TestResultsXmlFormatter.STATUS_ERROR, TestResultsXmlFormatter.STATUS_FAILED -> myProcessor.onTestFailure(
            new TestFailedEvent(myCurrentTest, "", currentText, myStatus.equals(TestResultsXmlFormatter.STATUS_ERROR), null, null)
          );
          case TestResultsXmlFormatter.STATUS_IGNORED, TestResultsXmlFormatter.STATUS_SKIPPED-> myProcessor.onTestIgnored(
            new TestIgnoredEvent(myCurrentTest, "", currentText)
          );
        }
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
