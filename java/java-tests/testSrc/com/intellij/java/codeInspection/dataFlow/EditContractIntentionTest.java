// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.dataFlow.EditContractIntention;
import com.intellij.modcommand.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class EditContractIntentionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testEditContract() {
    EditContractIntention intention = new EditContractIntention();
    PsiClass stringClass = JavaPsiFacade.getInstance(getProject()).findClass("java.lang.String", GlobalSearchScope.allScope(getProject()));
    assertNotNull(stringClass);
    PsiMethod constructor = ContainerUtil.find(
      stringClass.getConstructors(), ctr -> ctr.getParameterList().getParametersCount() == 3 &&
                                            PsiTypes.intType().equals(ctr.getParameterList().getParameter(1).getType()));
    assertNotNull(constructor);
    int offset = constructor.getTextOffset();
    ActionContext ctx = new ActionContext(getProject(), constructor.getContainingFile(), offset, TextRange.create(offset, offset), null);
    Presentation presentation = intention.getPresentation(ctx);
    assertNotNull(presentation);
    assertEquals("Add method contract to 'String()'…", presentation.name());
    ModEditOptions<?> editOptions = assertInstanceOf(intention.perform(ctx), ModEditOptions.class);
    ModCommandAction action = ContainerUtil.getOnlyItem(
      assertInstanceOf(editOptions.applyOptions(Map.of("impure", false)), ModChooseAction.class).actions());
    assertNotNull(action);
    ModUpdateFileText finalAction = assertInstanceOf(action.perform(ctx), ModUpdateFileText.class);

    IntentionPreviewInfo.CustomDiff customDiff =
      assertInstanceOf(ModCommandExecutor.getInstance().getPreview(finalAction, ctx), IntentionPreviewInfo.CustomDiff.class);
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
  }
}
