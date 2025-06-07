// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class JavaMainStringArgsContributorTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @NeedsIndex.Full
  public void testSimpleParametersImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(<caret>){}
        """);
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
      assertNotNull(item);
      getLookup().setCurrentItem(item);
      type("\n");
      myFixture.checkResult("""
                              void main(String[] args){}
                              """);
    });
  }

  @NeedsIndex.Full
  public void testSimpleParametersImplicitClassSmartCompletion() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(<caret>){}
        """);
      myFixture.complete(CompletionType.SMART);
      myFixture.checkResult("""
                              void main(String[] args){}
                              """);
    });
  }

  @NeedsIndex.Full
  public void testSimpleParametersNormalClass() {
    myFixture.configureByText("Test.java", """
      class A{ public static void main(<caret>){}}
      """);
    LookupElement[] items = myFixture.completeBasic();
    LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
    assertNotNull(item);
    getLookup().setCurrentItem(item);
    type("\n");
    myFixture.checkResult("""
                            class A{ public static void main(String[] args){}}
                            """);
  }

  @NeedsIndex.Full
  public void testSimpleParametersNormalClassNoCompletionBrokenReturnType() {
    myFixture.configureByText("Test.java", """
      class A{ public static int main(<caret>){}}
      """);
    LookupElement[] items = myFixture.completeBasic();
    LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
    assertNull(item);
  }

  @NeedsIndex.Full
  public void testSimpleParametersNormalClassNoCompletionSeveralParameters() {
    myFixture.configureByText("Test.java", """
      class A{ public static int main(String a, <caret>){}}
      """);
    LookupElement[] items = myFixture.completeBasic();
    LookupElement item = ContainerUtil.find(items, it -> it.getLookupString().contains("String[] args"));
    assertNull(item);
  }

  @NeedsIndex.Full
  public void testVariableImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(){<caret>}
        """);
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> {
        LookupElementPresentation presentation = new LookupElementPresentation();
        it.renderElement(presentation);
        return "String[] args".equals(presentation.getTypeText());
      });
      assertNotNull(item);
      getLookup().setCurrentItem(item);
      type("\n");
      myFixture.checkResult("""
                              void main(String[] args){args<caret>}
                              """);
    });
  }

  @NeedsIndex.Full
  public void testVariableImplicitClassNoCompletionDefinedArgs() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.configureByText("Test.java", """
        void main(){String args = "1"; <caret>}
        """);
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> {
        LookupElementPresentation presentation = new LookupElementPresentation();
        it.renderElement(presentation);
        return "String[] args".equals(presentation.getTypeText());
      });
      assertNull(item);
    });
  }

  @NeedsIndex.SmartMode(reason = "necessary for rendering other items")
  public void testVariableImplicitClassNoCompletionBrokenReturnType() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.configureByText("Test.java", """
        int main(){<caret>}
        """);
      LookupElement[] items = myFixture.completeBasic();
      LookupElement item = ContainerUtil.find(items, it -> {
        LookupElementPresentation presentation = new LookupElementPresentation();
        it.renderElement(presentation);
        return "String[] args".equals(presentation.getTypeText());
      });
      assertNull(item);
    });
  }
}
