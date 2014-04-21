/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.richcopy;

import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.view.RtfTransferableData;

/**
 * @author Denis Zhdanov
 * @since 3/25/13 2:18 PM
 */
public class RtfCopyPasteProcessor extends BaseTextWithMarkupCopyPasteProcessor<RtfTransferableData> {
  public RtfCopyPasteProcessor(TextWithMarkupProcessor processor) {
    super(processor);
  }

  @Override
  protected RtfTransferableData doBuild(SyntaxInfo info) {
    return new RtfTransferableData(info);
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class RawTextSetter extends BaseTextWithMarkupCopyPasteProcessor.RawTextSetter {
    public RawTextSetter(RtfCopyPasteProcessor processor) {
      super(processor);
    }
  }
}
