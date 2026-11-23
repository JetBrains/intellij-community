/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;

/**
 * @author Maxim.Mossienko
 */
@TestApplication
@IndexingModeTemplate
public class WordCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<NormalCompletionFixture> completion =
    normalCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/word/");

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testKeyWordCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("1.txt");
      completion.get().checkResultByFile("1_after.txt");
    
      completion.get().configureByFile("1.properties");
      completion.get().checkResultByFile("1_after.properties");

      completion.get().configureByFile("1.xml");
      completion.get().checkResultByFile("1_after.xml");

      completion.get().configureByFile("2.xml");
      completion.get().configureByFile("2_after.xml");
    });
  }

  @Test
  public void testNoWordCompletionForNonSoftReference() {
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiReferenceProvider softProvider = new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
          return new PsiReference[]{new PsiReferenceBase<>(element, true) {
            @Override
            public PsiElement resolve() {
              return null;
            }

            @Override
            public Object @NotNull [] getVariants() {
              return new Object[]{"MySoftVariant"};
            }
          }};
        }
      };
      final PsiReferenceProvider hardProvider = new PsiReferenceProvider() {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
          return new PsiReference[]{new PsiReferenceBase<>(element, false) {
            @Override
            public PsiElement resolve() {
              return null;
            }

            @Override
            public Object @NotNull [] getVariants() {
              return new Object[]{"MyHardVariant"};
            }
          }};
        }
      };
      PsiReferenceRegistrarImpl registrar =
        (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
      try {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), softProvider);
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), hardProvider);

        completion.get().configureByFile("3.java");
        completion.get().checkResultByFile("3_after.java");
      }
      finally {
        registrar.unregisterReferenceProvider(PsiLiteralExpression.class, softProvider);
        registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
      }
    });
  }

  @Test
  public void testInJavaLiterals() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testComments() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("4.java");
      completion.get().checkResultByFile("4_after.java");
    });
  }

  @Test
  public void testSpaceInComment() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testTextInComment() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testDollarsInPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".txt");
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.txt");
    });
  }

  @Test
  public void testCompleteStringLiteralCopy() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().selectItem(completion.get().getItems()[1]);
      completion.get().checkResult();
    });
  }

}
