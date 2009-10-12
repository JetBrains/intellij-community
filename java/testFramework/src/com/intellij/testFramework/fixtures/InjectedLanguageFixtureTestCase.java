/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;

/**
 * @author peter
 */
public abstract class InjectedLanguageFixtureTestCase extends LightCodeInsightFixtureTestCase {
  protected void checkCompletionVariants(final FileType fileType, final String text, final String... strings) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    myFixture.checkResult(text.replaceAll("\\|", "<caret>"));

    assertSameElements(ContainerUtil.map(elements, new Function<LookupElement, String>() {
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    }), strings);
  }

  protected void assertNoVariants(final FileType fileType, final String text) throws Throwable {
    checkCompleted(fileType, text, text);
  }

  protected void checkCompleted(final FileType fileType, final String text, final String resultText) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    if (elements != null && elements.length == 1) {
      new WriteCommandAction(getProject()) {
        protected void run(Result result) throws Throwable {
          ((LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup()).finishLookup(Lookup.NORMAL_SELECT_CHAR);
        }
      }.execute();
    }
    else if (elements != null && elements.length > 0) {
      fail(Arrays.toString(elements));
    }
    myFixture.checkResult(resultText.replaceAll("\\|", "<caret>"));
    FileDocumentManager.getInstance().saveDocument(myFixture.getDocument(myFixture.getFile()));
  }

  protected void tuneCompletionFile(PsiFile file) {
  }

}
