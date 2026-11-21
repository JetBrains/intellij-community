// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.AbstractTestProxy.SuiteResultPolicy;
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
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.testFrameworks.AbstractExpectedPatterns;
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
import java.util.Deque;
import java.util.List;
import java.util.Objects;

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
  private String myLocation;
  private String myDuration;
  private final List<TestProblem> myProblems = new ArrayList<>();
  private final List<TestOutputEvent> myOutput = new ArrayList<>();
  private TestIgnoredEvent myIgnoredEvent;
  private int myPropertiesLevel;
  private final Deque<TextElement> myTextElements = new ArrayDeque<>();

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
      myOutput.clear();
      myIgnoredEvent = null;
      myDuration = attributes.getValue(DURATION);
      String classNameValue = attributes.getValue(CLASSNAME);
      String classname = classNameValue == null ? null : StringUtil.unescapeXmlEntities(classNameValue);
      myLocation = "java:test://" + (StringUtil.isEmpty(classname) ? testName : classname + "/" + testName);
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
    else {
      var problemKind = ProblemKind.forElement(name);
      if (problemKind != null) {
        myTextElements.push(new TextElement(name,
                                            TextElementKind.PROBLEM,
                                            StringUtil.unescapeXmlEntities(StringUtil.notNullize(attributes.getValue(MESSAGE))),
                                            StringUtil.unescapeXmlEntities(StringUtil.notNullize(attributes.getValue(TYPE))),
                                            problemKind));
      }
    }
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    if (!myTextElements.isEmpty()) {
      myTextElements.peek().text.append(ch, start, length);
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
      long time;
      try {
        time = myDuration != null ? (long)(1000 * Float.parseFloat(myDuration)) : -1;
      }
      catch (NumberFormatException e) {
        time = -1;
      }
      emitTestCase(time);
      myCurrentTest = null;
      myLocation = null;
      myDuration = null;
      myProblems.clear();
      myOutput.clear();
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
      case PROBLEM -> myProblems.add(new TestProblem(Objects.requireNonNull(element.problemKind),
                                                    element.message, element.type, text, element.output));
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
      var output = new TestOutputEvent(myCurrentTest, text, !stderr);
      if (!myTextElements.isEmpty() && myTextElements.peek().kind == TextElementKind.PROBLEM) {
        myTextElements.peek().output.add(output);
      }
      else {
        myOutput.add(output);
      }
    }
    else {
      myProcessor.onUncapturedOutput(text, stderr ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
    }
  }

  private void emitTestCase(long time) {
    List<TestProblem> primary = myProblems.stream().filter(problem -> problem.kind.primary).toList();
    List<TestProblem> retries = myProblems.stream().filter(problem -> !problem.kind.primary).toList();
    if (retries.isEmpty()) {
      emitTest(myCurrentTest, myProblems, myOutput, time, myIgnoredEvent);
      return;
    }

    var suiteStarted = new TestSuiteStartedEvent(myCurrentTest, myLocation);
    suiteStarted.setSuiteResultPolicy(SuiteResultPolicy.LAST_CHILD);
    myProcessor.onSuiteStarted(suiteStarted);

    boolean passed = primary.isEmpty() && retries.stream().anyMatch(problem -> problem.kind.impliesSuccess);
    int count = retries.size() + (primary.isEmpty() ? 0 : 1) + (passed ? 1 : 0);
    int index = 1;
    if (!primary.isEmpty()) {
      emitTest(attemptName(index++, count), primary, myOutput, time, null);
    }
    for (TestProblem retry : retries) {
      boolean first = index == 1 && !passed;
      emitTest(attemptName(index++, count), List.of(retry), first ? myOutput : List.of(), first ? time : -1, null);
    }
    if (passed) {
      emitTest(attemptName(index, count), List.of(), myOutput, time, myIgnoredEvent);
    }
    myProcessor.onSuiteFinished(new TestSuiteFinishedEvent(myCurrentTest));
  }

  private static @NotNull String attemptName(int index, int count) {
    return ExecutionBundle.message("junit.xml.import.repetition.name", index, count);
  }

  private void emitTest(@NotNull String name,
                        @NotNull List<TestProblem> problems,
                        @NotNull List<TestOutputEvent> output,
                        long time,
                        @Nullable TestIgnoredEvent ignored) {
    myProcessor.onTestStarted(new TestStartedEvent(name, myLocation));
    emitTestOutput(name, output);
    for (TestProblem problem : problems) {
      emitTestOutput(name, problem.output);
    }
    if (!problems.isEmpty()) {
      myProcessor.onTestFailure(createTestFailedEvent(name, problems));
    }
    else if (ignored != null) {
      myProcessor.onTestIgnored(new TestIgnoredEvent(name, ignored.getIgnoreComment(), ignored.getStacktrace()));
    }
    myProcessor.onTestFinished(new TestFinishedEvent(name, time));
  }

  private void emitTestOutput(@NotNull String name, @NotNull List<TestOutputEvent> output) {
    for (TestOutputEvent event : output) {
      myProcessor.onTestOutput(new TestOutputEvent(name, event.getText(), event.getOutputType() == ProcessOutputTypes.STDOUT));
    }
  }

  private static @NotNull TestFailedEvent createTestFailedEvent(@NotNull String name, @NotNull List<TestProblem> problems) {
    TestProblem first = problems.getFirst();
    String message = first.getMessage();
    StringBuilder stackTrace = new StringBuilder(first.text);
    for (int i = 1; i < problems.size(); i++) {
      TestProblem problem = problems.get(i);
      if (!stackTrace.isEmpty()) {
        stackTrace.append('\n');
      }
      stackTrace.append(problem.getMessage());
      if (!StringUtil.isEmpty(problem.type)) {
        stackTrace.append(" (").append(problem.type).append(')');
      }
      if (!StringUtil.isEmpty(problem.text)) {
        stackTrace.append('\n').append(problem.text);
      }
      stackTrace.append('\n');
    }
    ComparisonFailureData comparisonData = AbstractExpectedPatterns.parseComparisonFailure(message);
    String actual = comparisonData != null ? comparisonData.getActual() : null;
    String expected = comparisonData != null ? comparisonData.getExpected() : null;
    return new TestFailedEvent(name, message, stackTrace.toString(), first.kind.error, actual, expected);
  }

  private @Nullable TextElement pollTextElement(@NotNull String name) {
    if (myTextElements.isEmpty() || !name.equals(myTextElements.peek().name)) return null;
    return myTextElements.pop();
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
    PROBLEM
  }

  private static final class TextElement {
    final @NotNull String name;
    final @NotNull TextElementKind kind;
    final @Nullable String message;
    final @Nullable String type;
    final @Nullable ProblemKind problemKind;
    final @NotNull StringBuilder text = new StringBuilder();
    final @NotNull List<TestOutputEvent> output = new ArrayList<>();

    TextElement(@NotNull String name, @NotNull TextElementKind kind) {
      this(name, kind, null, null, null);
    }

    TextElement(@NotNull String name, @NotNull TextElementKind kind, @Nullable String message, @Nullable String type) {
      this(name, kind, message, type, null);
    }

    TextElement(@NotNull String name,
                @NotNull TextElementKind kind,
                @Nullable String message,
                @Nullable String type,
                @Nullable ProblemKind problemKind) {
      this.name = name;
      this.kind = kind;
      this.message = message;
      this.type = type;
      this.problemKind = problemKind;
    }
  }

  private record TestProblem(@NotNull ProblemKind kind,
                             @Nullable String message,
                             @Nullable String type,
                             @NotNull String text,
                             @NotNull List<TestOutputEvent> output) {
    @NotNull String getMessage() {
      if (!StringUtil.isEmpty(message)) return message;
      if (!StringUtil.isEmpty(type)) return type;
      return kind.error ? TestResultsXmlFormatter.STATUS_ERROR : TestResultsXmlFormatter.STATUS_FAILED;
    }
  }

  private enum ProblemKind {
    FAILURE("failure", false, true, false),
    ERROR("error", true, true, false),
    RERUN_FAILURE("rerunFailure", false, false, false),
    RERUN_ERROR("rerunError", true, false, false),
    FLAKY_FAILURE("flakyFailure", false, false, true),
    FLAKY_ERROR("flakyError", true, false, true);

    private final String element;
    private final boolean error;
    private final boolean primary;
    private final boolean impliesSuccess;

    ProblemKind(String element, boolean error, boolean primary, boolean impliesSuccess) {
      this.element = element;
      this.error = error;
      this.primary = primary;
      this.impliesSuccess = impliesSuccess;
    }

    static @Nullable ProblemKind forElement(@NotNull String name) {
      for (ProblemKind kind : values()) {
        if (kind.element.equals(name)) return kind;
      }
      return null;
    }
  }
}
