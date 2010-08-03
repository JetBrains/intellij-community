/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.util.PsiTreeUtil;

/**
* @author Maxim.Medvedev
*/
public class JavaClassNameInsertHandler implements AllClassesGetter.ClassNameInsertHandler {
  public AllClassesGetter.ClassNameInsertHandlerResult handleInsert(InsertionContext context,
                              JavaPsiClassReferenceElement item) {
    context.setAddCompletionChar(false);
    Editor editor = context.getEditor();
    PsiFile file = context.getFile();
    int endOffset = editor.getCaretModel().getOffset();
    if (file.getLanguage() == StdLanguages.JAVA) {
      if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, PsiImportStatementBase.class, false) != null) {
        return AllClassesGetter.ClassNameInsertHandlerResult.INSERT_FQN;
      }
      else {
        JavaPsiClassReferenceElement.JAVA_CLASS_INSERT_HANDLER.handleInsert(context, item);
      }
    }
    return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE;
  }
}
