// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.idea.TestFor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LightGuavaNullabilityHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor DESCRIPTOR =
    new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21, List.of("com.google.guava:guava:33.5.0-jre"));

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }
  
  @TestFor(issues = "IDEA-383075")
  public void testHighlightWithAnnotatedUpperBound() {
    myFixture.configureByText("Test.java", """
      import com.google.common.collect.Multimap;
      
      class Test {
        public interface InvocationOnMock {}
      
        public interface Answer<T> {
          T answer(InvocationOnMock invocation) throws Throwable;
        }
      
        private native Multimap<?, String> onInterceptedDaoCallReturningMultimap(InvocationOnMock invocation);
        public static native Object doAnswer(Answer answer);
      
        public void prepareMocks() {
          doAnswer(this::onInterceptedDaoCallReturningMultimap);
        }
      }""");
    myFixture.checkHighlighting();
  }
}
