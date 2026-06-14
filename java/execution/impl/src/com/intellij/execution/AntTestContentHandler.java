// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class AntTestContentHandler extends DefaultHandler {
  public static final class AntTestOutputExtension implements ImportTestOutputExtension {

    @Override
    public @Nullable DefaultHandler createHandler(@NotNull Reader reader, GeneralTestEventsProcessor processor) {
      final String[] rooName = new String[]{null};
      NanoXmlUtil.parse(reader, new NanoXmlBuilder() {
        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
        }

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
          rooName[0] = getElementName(name);
        }
      });
      return TESTSUITES.equals(rooName[0]) || TESTSUITE.equals(rooName[0]) ? new AntTestContentHandler(processor)
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
  private static final String RERUN_ERROR = "rerunError";
  private static final String RERUN_FAILURE = "rerunFailure";
  private static final String FLAKY_ERROR = "flakyError";
  private static final String FLAKY_FAILURE = "flakyFailure";
  private static final String SKIPPED = "skipped";
  private static final String IGNORED = "ignored";
  private static final String OUT = "system-out";
  private static final String ERR = "system-err";
  private static final String PROPERTIES = "properties";
  private static final String PROPERTY = "property";
  private static final String MESSAGE = "message";
  private static final String TYPE = "type";
  private static final String VALUE = "value";

  private final GeneralTestEventsProcessor myProcessor;
  private final Stack<String> mySuites = new Stack<>();
  private String myCurrentTest;
  private String myDuration;
  private final List<TestProblem> myProblems = new ArrayList<>();
  private TestIgnoredEvent myIgnoredEvent;
  private int myPropertiesLevel;
  private final ArrayDeque<TextElement> myTextElements = new ArrayDeque<>();

  public AntTestContentHandler(GeneralTestEventsProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    String name = getElementName(qName);
    if (TESTSUITE.equals(name)) {
      String nameValue = attributes.getValue(NAME);
      final String suiteName = nameValue == null ? "" : StringUtil.unescapeXmlEntities(nameValue);
      String packageValue = attributes.getValue(PACKAGE);
      final String packageName = packageValue == null ? null : StringUtil.unescapeXmlEntities(packageValue);
      myProcessor
        .onSuiteStarted(new TestSuiteStartedEvent(suiteName, "java:suite://" + StringUtil.getQualifiedName(packageName,
                                                                                                           StringUtil.notNullize(suiteName))));
      mySuites.push(suiteName);
    }
    else if (TESTCASE.equals(name)) {
      String nameValue = attributes.getValue(NAME);
      final String testName = nameValue == null ? "" : StringUtil.unescapeXmlEntities(nameValue);
      myCurrentTest = testName;
      myProblems.clear();
      myIgnoredEvent = null;
      myDuration = attributes.getValue(DURATION);
      String classNameValue = attributes.getValue(CLASSNAME);
      String classname = classNameValue == null ? null : StringUtil.unescapeXmlEntities(classNameValue);
      String location = StringUtil.isEmpty(classname) ? testName : classname + "/" + testName;
      final TestStartedEvent startedEvent = new TestStartedEvent(testName, "java:test://" + location);
      myProcessor.onTestStarted(startedEvent);
    }
    else if (OUT.equals(name) || ERR.equals(name)) {
      myTextElements.push(new TextElement(name, OUT.equals(name) ? TextElementKind.OUTPUT : TextElementKind.ERROR_OUTPUT));
    }
    else if (PROPERTIES.equals(name)) {
      myPropertiesLevel++;
    }
    else if (PROPERTY.equals(name) && myPropertiesLevel > 0) {
      String propertyName = attributes.getValue(NAME);
      String propertyValue = attributes.getValue(VALUE);
      myTextElements.push(new TextElement(name, TextElementKind.PROPERTY,
                                          propertyName == null ? "" : StringUtil.unescapeXmlEntities(propertyName),
                                          propertyValue == null ? null : StringUtil.unescapeXmlEntities(propertyValue)));
    }
    else if (SKIPPED.equals(name) || IGNORED.equals(name)) {
      myTextElements.push(new TextElement(name, TextElementKind.IGNORED,
                                          StringUtil.unescapeXmlEntities(StringUtil.notNullize(attributes.getValue(MESSAGE))),
                                          null));
    }
    else if (isProblemElement(name)) {
      boolean error = ERROR.equals(name) || RERUN_ERROR.equals(name) || FLAKY_ERROR.equals(name);
      boolean primary = ERROR.equals(name) || FAILURE.equals(name);
      myTextElements.push(new TextElement(name,
                                          error ? TextElementKind.ERROR : TextElementKind.FAILURE,
                                          StringUtil.unescapeXmlEntities(StringUtil.notNullize(attributes.getValue(MESSAGE))),
                                          StringUtil.unescapeXmlEntities(StringUtil.notNullize(attributes.getValue(TYPE))),
                                          primary,
                                          getProblemLabel(name)));
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    for (TextElement element : myTextElements) {
      element.text.append(ch, start, length);
    }
  }

  @Override
  public void endElement(String uri, String localName, String qName) {
    String name = getElementName(qName);
    TextElement textElement = pollTextElement(name);
    if (textElement != null) {
      handleTextElement(textElement);
    }

    if (TESTSUITE.equals(name)) {
      myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(mySuites.pop()));
    }
    else if (TESTCASE.equals(name)) {
      TestFailedEvent failedEvent = createTestFailedEvent();
      if (failedEvent != null) {
        myProcessor.onTestFailure(failedEvent);
      }
      else {
        emitNonPrimaryProblems();
        if (myIgnoredEvent != null) {
          myProcessor.onTestIgnored(myIgnoredEvent);
        }
      }
      long time;
      try {
        time = myDuration != null ? (long)(1000 * Float.parseFloat(myDuration)) : -1;
      }
      catch (NumberFormatException e) {
        time = -1;
      }
      myProcessor.onTestFinished(new TestFinishedEvent(myCurrentTest, time));
      myCurrentTest = null;
      myDuration = null;
      myProblems.clear();
      myIgnoredEvent = null;
    }
    else if (PROPERTIES.equals(name) && myPropertiesLevel > 0) {
      myPropertiesLevel--;
    }
  }

  private void handleTextElement(@NotNull TextElement element) {
    String text = StringUtil.unescapeXmlEntities(element.text.toString());
    switch (element.kind) {
      case OUTPUT -> emitOutput(text, false);
      case ERROR_OUTPUT -> emitOutput(text, true);
      case PROPERTY -> emitProperty(element.message, element.type != null ? element.type : text);
      case IGNORED -> myIgnoredEvent = new TestIgnoredEvent(myCurrentTest,
                                                            StringUtil.notNullize(element.message),
                                                            text);
      case FAILURE, ERROR -> {
        TestProblem problem = new TestProblem(element.kind == TextElementKind.ERROR,
                                             element.primaryResult,
                                             element.message,
                                             element.type,
                                             element.label,
                                             text);
        myProblems.add(problem);
      }
    }
  }

  private void emitProperty(@Nullable String name, @Nullable String value) {
    if (StringUtil.isEmpty(name) && StringUtil.isEmpty(value)) return;

    StringBuilder text = new StringBuilder("Property");
    if (!StringUtil.isEmpty(name)) {
      text.append(' ').append(name);
    }
    if (!StringUtil.isEmpty(value)) {
      text.append('=').append(value);
    }
    text.append('\n');
    emitOutput(text.toString(), false);
  }

  private void emitOutput(@Nullable String text, boolean stderr) {
    if (StringUtil.isEmpty(text)) return;

    if (myCurrentTest != null) {
      myProcessor.onTestOutput(new TestOutputEvent(myCurrentTest, text, !stderr));
    }
    else {
      myProcessor.onUncapturedOutput(text, stderr ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
    }
  }

  private @Nullable TestFailedEvent createTestFailedEvent() {
    int firstPrimaryIndex = -1;
    for (int i = 0; i < myProblems.size(); i++) {
      if (myProblems.get(i).primary) {
        firstPrimaryIndex = i;
        break;
      }
    }
    if (firstPrimaryIndex < 0) return null;

    TestProblem first = myProblems.get(firstPrimaryIndex);
    String message = first.getMessage();
    StringBuilder stackTrace = new StringBuilder(first.text);
    for (int i = 0; i < myProblems.size(); i++) {
      if (i == firstPrimaryIndex) continue;
      TestProblem problem = myProblems.get(i);
      if (!stackTrace.isEmpty()) {
        stackTrace.append('\n');
      }
      stackTrace.append(problem.formatForOutput());
    }
    return new TestFailedEvent(myCurrentTest, message, stackTrace.toString(), first.error, null, null);
  }

  private void emitNonPrimaryProblems() {
    for (TestProblem problem : myProblems) {
      if (!problem.primary) {
        emitOutput(problem.formatForOutput(), true);
      }
    }
  }

  private @Nullable TextElement pollTextElement(@NotNull String name) {
    if (myTextElements.isEmpty() || !name.equals(myTextElements.peek().name)) return null;
    return myTextElements.pop();
  }

  private static boolean isProblemElement(@NotNull String name) {
    return ERROR.equals(name) || FAILURE.equals(name) ||
           RERUN_ERROR.equals(name) || RERUN_FAILURE.equals(name) ||
           FLAKY_ERROR.equals(name) || FLAKY_FAILURE.equals(name);
  }

  private static @NotNull String getProblemLabel(@NotNull String name) {
    return switch (name) {
      case ERROR -> "Error";
      case FAILURE -> "Failure";
      case RERUN_ERROR -> "Rerun error";
      case RERUN_FAILURE -> "Rerun failure";
      case FLAKY_ERROR -> "Flaky error";
      case FLAKY_FAILURE -> "Flaky failure";
      default -> "Problem";
    };
  }

  private static @NotNull String getElementName(@NotNull String qName) {
    int index = qName.indexOf(':');
    return index < 0 ? qName : qName.substring(index + 1);
  }

  private enum TextElementKind {
    OUTPUT,
    ERROR_OUTPUT,
    PROPERTY,
    IGNORED,
    FAILURE,
    ERROR
  }

  private static final class TextElement {
    final @NotNull String name;
    final @NotNull TextElementKind kind;
    final @Nullable String message;
    final @Nullable String type;
    final boolean primaryResult;
    final @NotNull String label;
    final @NotNull StringBuilder text = new StringBuilder();

    TextElement(@NotNull String name, @NotNull TextElementKind kind) {
      this(name, kind, null, null, false, "");
    }

    TextElement(@NotNull String name, @NotNull TextElementKind kind, @Nullable String message, @Nullable String type) {
      this(name, kind, message, type, false, "");
    }

    TextElement(@NotNull String name,
                @NotNull TextElementKind kind,
                @Nullable String message,
                @Nullable String type,
                boolean primaryResult,
                @NotNull String label) {
      this.name = name;
      this.kind = kind;
      this.message = message;
      this.type = type;
      this.primaryResult = primaryResult;
      this.label = label;
    }
  }

  private static final class TestProblem {
    final boolean error;
    final boolean primary;
    final @NotNull String message;
    final @NotNull String type;
    final @NotNull String label;
    final @NotNull String text;

    TestProblem(boolean error,
                boolean primary,
                @Nullable String message,
                @Nullable String type,
                @NotNull String label,
                @Nullable String text) {
      this.error = error;
      this.primary = primary;
      this.message = StringUtil.notNullize(message);
      this.type = StringUtil.notNullize(type);
      this.label = label;
      this.text = StringUtil.notNullize(text);
    }

    @NotNull String getMessage() {
      if (!StringUtil.isEmpty(message)) return message;
      if (!StringUtil.isEmpty(type)) return type;
      return error ? TestResultsXmlFormatter.STATUS_ERROR : TestResultsXmlFormatter.STATUS_FAILED;
    }

    @NotNull String formatForOutput() {
      StringBuilder builder = new StringBuilder(primary ? getMessage() : label);
      if (!StringUtil.isEmpty(type)) {
        builder.append(" (").append(type).append(')');
      }
      if (!StringUtil.isEmpty(text)) {
        builder.append('\n').append(text);
      }
      builder.append('\n');
      return builder.toString();
    }
  }
}
