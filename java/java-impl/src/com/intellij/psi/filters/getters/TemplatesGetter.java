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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.04.2003
 * Time: 19:37:23
 * To change this template use Options | File Templates.
 */
public class TemplatesGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final List result = new ArrayList();
    final TemplateSettings templateSettings = TemplateSettings.getInstance();
    final TemplateImpl[] templates = templateSettings.getTemplates();

    for (final TemplateImpl template : templates) {
      if (template.isDeactivated()) continue;

      final TemplateContext templateContext = template.getTemplateContext();

      if (!templateContext.isEnabled(new SmartCompletionContextType())) {
        continue;
      }
      result.add(template);
    }

    return result.toArray();
  }
}
