// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.idea.TestFor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class LightGuavaNullabilityHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR =
    new DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21, List.of("com.google.guava:guava:33.5.0-jre"));
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  
  @Test
  @TestFor(issues = "IDEA-383075")
  public void testHighlightWithAnnotatedUpperBound() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
