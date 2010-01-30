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
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 6, 2007
 * Time: 3:09:55 PM
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

public class LossyEncodingInspection extends BaseJavaLocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("lossy.encoding");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "LossyEncoding";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (ArrayUtil.find(file.getPsiRoots(), file) != 0) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String text = file.getText();
    Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);

    // no sense in checking transparently decoded file: all characters there are already safely encoded
    if (charset instanceof Native2AsciiCharset) return null;

    int errorCount = 0;
    int start = -1;
    List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();
    for (int i = 0; i <= text.length(); i++) {
      char c = i == text.length() ? 0 : text.charAt(i);
      if (i == text.length() || isRepresentable(c, charset)) {
        if (start != -1) {
          ProblemDescriptor descriptor = manager.createProblemDescriptor(file, new TextRange(start, i), InspectionsBundle.message(
            "unsupported.character.for.the.charset", charset), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
          descriptors.add(descriptor);
          start = -1;

          //do not report too many errors
          if (errorCount++ > 200) break;
        }
      }
      else {
        if (start == -1) {
          start = i;
        }
      }
    }

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static boolean isRepresentable(final char c, final Charset charset) {
    String str = Character.toString(c);
    ByteBuffer out = charset.encode(str);
    CharBuffer buffer = charset.decode(out);
    return str.equals(buffer.toString());
  }
}
