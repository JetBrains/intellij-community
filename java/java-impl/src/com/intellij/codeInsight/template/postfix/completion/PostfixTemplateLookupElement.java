/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class PostfixTemplateLookupElement extends CustomLiveTemplateLookupElement {
  @NotNull
  private final PostfixTemplate myTemplate;

  public PostfixTemplateLookupElement(@NotNull PostfixLiveTemplate liveTemplate,
                                      @NotNull PostfixTemplate postfixTemplate,
                                      @NotNull String templateKey,
                                      boolean sudden) {
    super(liveTemplate, templateKey, postfixTemplate.getPresentableName(), postfixTemplate.getDescription(), sudden, true);
    myTemplate = postfixTemplate;
  }

  @NotNull
  public PostfixTemplate getPostfixTemplate() {
    return myTemplate;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    if (sudden) {
      presentation.setTailText(" " + arrow() + " " + myTemplate.getExample());
    }
    else {
      presentation.setTypeText(myTemplate.getExample());
      presentation.setTypeGrayed(true);
    }
  }

  private static String arrow() {
    return SystemInfo.isMac ? "→" : "->";
  }
}
