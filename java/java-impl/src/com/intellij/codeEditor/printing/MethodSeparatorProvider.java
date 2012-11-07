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

/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MethodSeparatorProvider extends FileSeparatorProvider {
  @Override
  public List<LineMarkerInfo> getFileSeparators(final PsiFile file, final Document document, @Nullable final Editor editor) {
    final List<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>();
    LineMarkersPass pass = new LineMarkersPass(file.getProject(), file, editor, document, 0, file.getTextLength(), true);
    for (LineMarkerInfo lineMarkerInfo : pass.queryLineMarkers()) {
      if (lineMarkerInfo.separatorColor != null) {
        result.add(lineMarkerInfo);
      }
    }

    Collections.sort(result, new Comparator<LineMarkerInfo>() {
      public int compare(final LineMarkerInfo i1, final LineMarkerInfo i2) {
        return i1.startOffset - i2.startOffset;
      }
    });
    return result;
  }
}