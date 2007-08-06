/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 6, 2007
 * Time: 3:09:55 PM
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class LossyEncodingInspection extends LocalInspectionTool {
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
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String text = file.getText();
    Charset charset = LoadTextUtil.getCharsetForWriting(file.getProject(), virtualFile, text);

    for (int i=0; i<text.length();i++) {
      char c = text.charAt(i);
      String str = Character.toString(c);
      ByteBuffer out = charset.encode(str);
      CharBuffer buffer = charset.decode(out);
      if (!str.equals(buffer.toString())) {
        ProblemDescriptor descriptor = manager.createProblemDescriptor(file,
                                                                       new TextRange(i,i+1),
                                                                       InspectionsBundle.message("unsupported.character.for.the.charset.0", charset),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        return new ProblemDescriptor[]{descriptor};
      }
    }
    return null;
  }
}