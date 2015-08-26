/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.SubLookupElement;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ChainCompletionMethodCallLookupElement extends JavaMethodCallElement implements StaticallyImportable {
  public static final String PROP_METHODS_CHAIN_COMPLETION_AUTO_COMPLETION = "methods.chain.completion.autoCompletion";

  private final PsiMethod myMethod;
  @Nullable
  private final TIntObjectHashMap<SubLookupElement> myReplaceElements;
  private final boolean myMergedOverloads;

  public ChainCompletionMethodCallLookupElement(final PsiMethod method,
                                                final @Nullable TIntObjectHashMap<SubLookupElement> replaceElements,
                                                final boolean shouldImportStatic,
                                                final boolean mergedOverloads) {
    super(method, shouldImportStatic, mergedOverloads);
    myMethod = method;
    myReplaceElements = replaceElements;
    myMergedOverloads = mergedOverloads;
    configureAutoCompletionPolicy();
  }

  public ChainCompletionMethodCallLookupElement(final PsiMethod method,
                                                final @Nullable TIntObjectHashMap<SubLookupElement> replaceElements) {
    super(method);
    myMethod = method;
    myReplaceElements = replaceElements;
    myMergedOverloads = true;
    configureAutoCompletionPolicy();
  }

  private void configureAutoCompletionPolicy() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (PropertiesComponent.getInstance(myMethod.getProject()).getBoolean(PROP_METHODS_CHAIN_COMPLETION_AUTO_COMPLETION)) {
        setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
      }
    }
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    super.handleInsert(context);
    if (!myMergedOverloads || isUniqueMethod(myMethod)) {
      context.commitDocument();
      context.getDocument()
        .insertString(context.getTailOffset() - 1, ChainCompletionLookupElementUtil.fillMethodParameters(myMethod, myReplaceElements));
      final PsiFile file = context.getFile();
      assert file instanceof PsiJavaFile;
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      if (myReplaceElements != null) {
        myReplaceElements.forEachValue(new TObjectProcedure<SubLookupElement>() {
          @Override
          public boolean execute(final SubLookupElement subLookupElement) {
            subLookupElement.doImport(javaFile);
            return true;
          }
        });
      }
      context.commitDocument();
      context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
      context.commitDocument();
    }
  }


  private static boolean isUniqueMethod(@NotNull final PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    return containingClass == null || containingClass.findMethodsByName(method.getName(), true).length == 1;
  }
}
