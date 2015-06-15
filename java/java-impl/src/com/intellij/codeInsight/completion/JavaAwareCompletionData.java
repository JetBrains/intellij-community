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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaAwareCompletionData extends CompletionData{

  @Override
  protected void addLookupItem(Set<LookupElement> set, final TailType tailType, @NotNull Object completion, final PsiFile file, final CompletionVariant variant) {
    if (completion instanceof LookupElement && !(completion instanceof LookupItem)) {
      set.add((LookupElement)completion);
      return;
    }

    LookupElement _ret = LookupItemUtil.objectToLookupItem(completion);
    if(_ret == null || !(_ret instanceof LookupItem)) return;

    LookupItem ret = (LookupItem)_ret;
    ret.setInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        if (context.shouldAddCompletionChar()) {
          return;
        }
        if (tailType != TailType.NONE && tailType.isApplicable(context)) {
          tailType.processTail(context.getEditor(), context.getTailOffset());
        }
      }
    });

    final Map<Object, Object> itemProperties = variant.getItemProperties();
    for (final Object key : itemProperties.keySet()) {
      ret.setAttribute(key, itemProperties.get(key));
    }

    set.add(ret);
  }

  protected void addKeyword(Set<LookupElement> set,
                            final TailType tailType,
                            final Object comp,
                            final PrefixMatcher matcher,
                            final PsiFile file,
                            final CompletionVariant variant) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    for (final LookupElement item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    try{
      final PsiKeyword keyword = factory.createKeyword((String)comp);
      addLookupItem(set, tailType, keyword, file, variant);
    }
    catch(IncorrectOperationException e){
      addLookupItem(set, tailType, comp, file, variant);
    }
  }

}
