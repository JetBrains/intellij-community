// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.dataFlow.EditRangeIntention;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class EditRangeIntentionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testEditRange() {
    EditRangeIntention intention = new EditRangeIntention();
    PsiClass stringClass = JavaPsiFacade.getInstance(getProject()).findClass("java.lang.String", GlobalSearchScope.allScope(getProject()));
    assertNotNull(stringClass);
    PsiMethod constructor = ContainerUtil.find(
      stringClass.getConstructors(), ctr -> ctr.getParameterList().getParametersCount() == 3 &&
                                            PsiTypes.intType().equals(ctr.getParameterList().getParameter(1).getType()));
    assertNotNull(constructor);
    PsiParameter parameter = constructor.getParameterList().getParameters()[1];
    int offset = parameter.getTextRange().getStartOffset();
    ActionContext ctx = new ActionContext(getProject(), parameter.getContainingFile(), offset, TextRange.create(offset, offset), null);
    Presentation presentation = intention.getPresentation(ctx);
    assertNotNull(presentation);
    assertEquals("Add range to 'offset'…", presentation.name());
    ModEditOptions<?> editOptions = assertInstanceOf(intention.perform(ctx), ModEditOptions.class);
    ModCommandAction action = ContainerUtil.getOnlyItem(
      assertInstanceOf(editOptions.applyOptions(Map.of("min", "1", "max", "5")), ModChooseAction.class).actions());
    assertNotNull(action);
    ModUpdateFileText finalAction = assertInstanceOf(action.perform(ctx), ModUpdateFileText.class);

    IntentionPreviewInfo.CustomDiff customDiff =
      assertInstanceOf(ModCommandExecutor.getInstance().getPreview(finalAction, ctx), IntentionPreviewInfo.CustomDiff.class);
    IntentionPreviewDiffResult result = IntentionPreviewDiffResult.fromCustomDiff(customDiff);
    List<IntentionPreviewDiffResult.@NotNull DiffInfo> diffs = result.getDiffs();
    assertEquals(2, diffs.size());
    assertEquals("<!-- annotations.xml -->", diffs.get(0).getFileText());
    assertEquals("""
                   <item name='java.lang.String String(char[], int, int) 1'>
                       <annotation name='org.jetbrains.annotations.Range'>
                           <val name="from" val="1"/>
                           <val name="to" val="5"/>
                       </annotation>
                   </item>""", diffs.get(1).getFileText());
  }
}
