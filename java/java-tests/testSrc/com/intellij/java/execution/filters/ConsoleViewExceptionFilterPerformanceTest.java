// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.filters;

import com.intellij.execution.filters.ExceptionFilters;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public class ConsoleViewExceptionFilterPerformanceTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  public void testExceptionFilterPerformance() {
    String trace = """
      java.lang.RuntimeException
      \tat ExceptionTest.main(ExceptionTest.java:5)
      \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
      \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:64)
      \tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
      \tat java.base/java.lang.reflect.Method.invoke(Method.java:564)
      \tat com.intellij.rt.execution.application.AppMainV2.main(AppMainV2.java:114)
      """;
    myFixture.addClass("""
                         public class ExceptionTest {
                             public static void main(String[] args) {
                                 //noinspection InfiniteLoopStatement
                                 while (true) {
                                     new RuntimeException().printStackTrace();
                                 }
                             }
                         }""");
    PlatformTestUtil.startPerformanceTest("Many exceptions", 10_000, () -> {
      // instantiate console with exception filters only to avoid failures due to Node/Ruby/etc dog-slow sloppy regex-based filters
      TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(getProject());
      consoleBuilder.filters(ExceptionFilters.getFilters(GlobalSearchScope.allScope(getProject())));
      ((TextConsoleBuilderImpl)consoleBuilder).setUsePredefinedMessageFilter(false);
      ConsoleViewImpl console = (ConsoleViewImpl)consoleBuilder.getConsole();
      console.getComponent(); // initConsoleEditor()
      ProcessHandler processHandler = new NopProcessHandler();
      processHandler.startNotify();
      console.attachToProcess(processHandler);

      try {
        console.setUpdateFoldingsEnabled(false); // avoid spending time on foldings
        console.print("start\n", ConsoleViewContentType.NORMAL_OUTPUT);
        console.flushDeferredText();
        console.getEditor().getCaretModel().moveToOffset(0); // avoid stick-to-end
        console.getEditor().getScrollingModel().scroll(0, 0); // avoid stick-to-end
        DocumentUtil.executeInBulk(console.getEditor().getDocument(), ()-> { // avoid editor size validation
          for (int i = 0; i < 25; i++) {
            for (int j = 0; j < 1_000; j++) {
              console.print(trace, ConsoleViewContentType.ERROR_OUTPUT);
            }
            // Write action is necessary to apply filters right in the current thread
            // see AsyncFilterRunner#highlightHyperlinks
            WriteAction.run(console::flushDeferredText);
          }
        });
        console.waitAllRequests();
      }
      finally {
        Disposer.dispose(console);
      }
    }).assertTiming();
  }
}
