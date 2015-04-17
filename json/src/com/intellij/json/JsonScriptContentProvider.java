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
package com.intellij.json;/*
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

import com.intellij.javascript.JSScriptContentProvider;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Json as  embedded script
 *
 * @author Ilya.Kazakevich
 */
public final class JsonScriptContentProvider implements HtmlScriptContentProvider {

  private static final JsonEmbedded JSON_EMBEDDED = new JsonEmbedded();

  @Override
  public IElementType getScriptElementType() {
    return JSON_EMBEDDED;
  }

  @Nullable
  @Override
  public Lexer getHighlightingLexer() {
    // TODO: temporary solution:
    /**
     * We do not have highlighting JSON lexer for embedded environment for now, so we reuse JScript
     */
    return new JSScriptContentProvider().getHighlightingLexer();
  }

  private static final class JsonEmbedded extends ILazyParseableElementType {
    private JsonEmbedded() {
      super("JSON_EMBEDDED", JsonLanguage.INSTANCE);
    }
  }
}
