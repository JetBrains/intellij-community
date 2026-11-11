// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins that a "Find in Path" search in a {@link LocalSearchScope} of a stub-backed element may load the AST of the element's file.
 * The scope check needs the tree; only the text match runs under the AST loading filter (see {@code FindInProjectUtil}).
 */
@TestApplication
public class FindInLocalScopeAstLoadingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSearchInLocalScopeOfStubBasedElementLoadsItsTree() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("ast.loading.filter").setValue(true, myFixture.getTestRootDisposable());
    VirtualFile file = myFixture.getTempDirFixture().createFile(
      "p/A.java", "package p; class A { void m() { int needle = 1; } void other() { int needle = 2; } }");
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
    PsiMethod method = ReadAction.computeBlocking(() -> {
      PsiClass aClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("p.A", GlobalSearchScope.projectScope(myFixture.getProject()));
      assertNotNull(aClass);
      return aClass.findMethodsByName("m", false)[0];
    });
    assertNull(((PsiFileImpl)method.getContainingFile()).getTreeElement(), "the AST of the scope file before the search");

    FindModel model = new FindModel();
    model.setStringToFind("needle");
    model.setMultipleFiles(true);
    model.setProjectScope(false);
    model.setCustomScope(true);
    model.setCustomScope(new LocalSearchScope(method));
    List<UsageInfo> usages = Collections.synchronizedList(new ArrayList<>());
    List<String> errors = Collections.synchronizedList(new ArrayList<>());
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details,
                                               @Nullable Throwable t) {
        errors.add(t == null ? message : message + ": " + ExceptionUtil.getThrowableText(t));
        return Action.NONE;
      }
    }, () -> FindInProjectUtil.findUsages(model, myFixture.getProject(), new CommonProcessors.CollectProcessor<>(usages),
                                          FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))));

    assertThat(errors).as("errors logged by the search").isNullOrEmpty();
    assertNotNull(((PsiFileImpl)method.getContainingFile()).getTreeElement(), "the AST of the scope file after the search");
    assertThat(usages).hasSize(1);
    assertEquals(file, usages.getFirst().getVirtualFile());
  }
}
