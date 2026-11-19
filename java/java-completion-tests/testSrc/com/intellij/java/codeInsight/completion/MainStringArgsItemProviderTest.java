// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class MainStringArgsItemProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightCompletionFixture> completion = lightCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.Full
  public void testSimpleParametersImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(<caret>){}
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
        assertNotNull(item);
        completion.get().getLookup().setCurrentItem(item);
        completion.get().type("\n");
        myFixture.checkResult("""
                                void main(String[] args){}
                                """);
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleParametersImplicitClassSmartCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(<caret>){}
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        myFixture.complete(CompletionType.SMART);
        myFixture.checkResult("""
                                void main(String[] args){}
                                """);
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleParametersNormalClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("Test.java", """
      class A{ public static void main(<caret>){}}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
      assertNotNull(item);
      completion.get().getLookup().setCurrentItem(item);
      completion.get().type("\n");
      myFixture.checkResult("""
                              class A{ public static void main(String[] args){}}
                              """);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleParametersNormalClassNoCompletionBrokenReturnType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("Test.java", """
      class A{ public static int main(<caret>){}}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
      assertNull(item);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testSimpleParametersNormalClassNoCompletionSeveralParameters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    myFixture.configureByText("Test.java", """
      class A{ public static int main(String a, <caret>){}}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
      assertNull(item);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testVariableImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(){<caret>}
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNotNull(item);
        completion.get().getLookup().setCurrentItem(item);
        completion.get().type("\n");
        myFixture.checkResult("""
                                void main(String[] args){args<caret>}
                                """);
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testVariableImplicitClassNoQualifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(){
          String a = "1";
          a.<caret>
        }
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNull(item);
      });
    });
  }

  @Test
  @NeedsIndex.Full
  public void testVariableImplicitClassNoCompletionDefinedArgs() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(){String args = "1"; <caret>}
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNull(item);
      });
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "necessary for rendering other items")
  public void testVariableImplicitClassNoCompletionBrokenReturnType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        int main(){<caret>}
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNull(item);
      });
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "necessary for rendering other items")
  public void testNoCompletionAfterErrorElementWithDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main() {
            String s = "1";.<caret>
        }
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNull(item);
      });
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "necessary for rendering other items")
  public void testNoCompletionAfterExpressionWithDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main() {
            String s = "1".<caret>;
        }
        """);
      EdtTestUtil.runInEdtAndWait(() -> {
        LookupElement[] items = myFixture.completeBasic();
        LookupElement item = ContainerUtil.find(items, it -> {
          LookupElementPresentation presentation = new LookupElementPresentation();
          it.renderElement(presentation);
          return "String[] args".equals(presentation.getTypeText());
        });
        assertNull(item);
      });
    });
  }


}
