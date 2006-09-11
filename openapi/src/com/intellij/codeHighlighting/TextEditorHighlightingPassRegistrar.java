/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.codeHighlighting;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 20-Apr-2006
 */
public abstract class TextEditorHighlightingPassRegistrar implements ProjectComponent {
  /**
   * @deprecated
   */
  public static final int FIRST = 0;
  /**
   * @deprecated
   */
  public static final int LAST = 1;
  /**
   * @deprecated
   */
  public static final int BEFORE = 3;
  /**
   * @deprecated
   */
  public static final int AFTER = 2;

  public enum Anchor {
    FIRST, LAST, BEFORE, AFTER
  }

  public static TextEditorHighlightingPassRegistrar getInstance(Project project){
    return project.getComponent(TextEditorHighlightingPassRegistrar.class);
  }

  /**
   * @deprecated
   */
  public abstract void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, int anchor, int anchorPass);

  public abstract void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory,
                                                          Anchor anchor,
                                                          int anchorPass,
                                                          boolean needAdditionalIntentionsPass,
                                                          boolean inPostHighlightingPass);

}
