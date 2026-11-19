// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@IndexingModeTemplate
public class Normal6CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testMakeMultipleArgumentsFinalWhenInInner() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      LookupElement item = ContainerUtil.find(completion.get().getLookup().getItems(), it -> "a, b".equals(it.getLookupString()));
      assertNotNull(item);
      completion.get().getLookup().setCurrentItem(item);
      completion.get().type("\n");
      completion.get().checkResult();
    });
  }

  @Test
  public void testOverwriteGenericsAfterNew() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExplicitTypeArgumentsWhenParameterTypesDoNotDependOnTypeParameters() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassNameWithGenericsTab2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\t")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassNameGenerics() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDoubleExpectedTypeFactoryMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      completion.get().assertStringItems("Key", "create", "create");
      assertEquals("Key.<Boolean>create", NormalCompletionFixture.renderElement(completion.get().getItems()[1]).getItemText());
      assertEquals("Key.create", NormalCompletionFixture.renderElement(completion.get().getItems()[2]).getItemText());
    });
  }
}
