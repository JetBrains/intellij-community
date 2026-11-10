// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.dataFlow.EditContractIntention;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModChooseAction;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public final class EditContractIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testEditContract() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EditContractIntention intention = new EditContractIntention();
    ReadAction.runBlocking(() -> {
      PsiClass stringClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("java.lang.String", GlobalSearchScope.allScope(myFixture.getProject()));
      assertNotNull(stringClass);
      PsiMethod constructor = ContainerUtil.find(
        stringClass.getConstructors(), ctr -> ctr.getParameterList().getParametersCount() == 3 &&
                                              PsiTypes.intType().equals(ctr.getParameterList().getParameter(1).getType()));
      assertNotNull(constructor);
      int offset = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> constructor.getTextOffset());
      ActionContext ctx = new ActionContext(myFixture.getProject(), constructor.getContainingFile(), offset, TextRange.create(offset, offset), null);
      Presentation presentation = intention.getPresentation(ctx);
      assertNotNull(presentation);
      assertEquals("Add method contract to 'String()'…", presentation.name());
      ModEditOptions<?> editOptions = assertInstanceOf(ModEditOptions.class, intention.perform(ctx));
      ModCommandAction action = ContainerUtil.getOnlyItem(
        assertInstanceOf(ModChooseAction.class, editOptions.applyOptions(Map.of("impure", false))).actions());
      assertNotNull(action);
      ModUpdateFileText finalAction = assertInstanceOf(ModUpdateFileText.class, action.perform(ctx));

      IntentionPreviewInfo.CustomDiff customDiff =
        assertInstanceOf(IntentionPreviewInfo.CustomDiff.class, ModCommandExecutor.getInstance().getPreview(finalAction, ctx));
      IntentionPreviewDiffResult result = IntentionPreviewDiffResult.fromCustomDiff(customDiff);
      List<IntentionPreviewDiffResult.@NotNull DiffInfo> diffs = result.getDiffs();
      assertEquals(2, diffs.size());
      assertEquals("<!-- annotations.xml -->", diffs.get(0).getFileText());
      assertEquals("""
                     <item name='java.lang.String String(char[], int, int)'>
                         <annotation name='org.jetbrains.annotations.Contract'>
                             <val name="pure" val="true"/>
                         </annotation>
                     </item>""", diffs.get(1).getFileText());
    });
  }
}
