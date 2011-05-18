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

import com.intellij.ide.DataManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class LossyEncodingInspection extends LocalInspectionTool {
  private static final LocalQuickFix CHANGE_ENCODING_FIX = new ChangeEncodingFix();
  private static final LocalQuickFix RELOAD_ENCODING_FIX = new ReloadInAnotherEncodingFix();

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
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
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) return null;
    if (ArrayUtil.find(file.getPsiRoots(), file) != 0) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    String text = file.getText();
    Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);

    // no sense in checking transparently decoded file: all characters there are already safely encoded
    if (charset instanceof Native2AsciiCharset) return null;

    List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();
    checkIfCharactersWillBeLostAfterSave(file, manager, isOnTheFly, text, charset, descriptors);

    checkForFileLoadedInWrongEncoding(file, manager, isOnTheFly, virtualFile, charset, descriptors);

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static void checkForFileLoadedInWrongEncoding(PsiFile file,
                                                        InspectionManager manager,
                                                        boolean isOnTheFly,
                                                        VirtualFile virtualFile,
                                                        Charset charset, List<ProblemDescriptor> descriptors) {
    if (!FileDocumentManager.getInstance().isFileModified(virtualFile) // when file is modified, it's too late to reload it
        && ChooseFileEncodingAction.isEnabled(virtualFile) // can't reload in another encoding, no point trying
      ) {
      // check if file was loaded in correct encoding
      byte[] bytes;
      try {
        bytes = virtualFile.contentsToByteArray();
      }
      catch (IOException e) {
        return;
      }
      String separator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, file.getProject());
      String toSave = StringUtil.convertLineSeparators(file.getText(), separator);
      byte[] bytesToSave = toSave.getBytes(charset);
      if (!Arrays.equals(bytesToSave, bytes)) {
        descriptors.add(manager.createProblemDescriptor(file, "File was loaded in a wrong encoding: '"+charset+"'",
                                                        RELOAD_ENCODING_FIX, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
      }
    }
  }

  private static void checkIfCharactersWillBeLostAfterSave(PsiFile file,
                                                           InspectionManager manager,
                                                           boolean isOnTheFly,
                                                           String text,
                                                           Charset charset, List<ProblemDescriptor> descriptors) {
    int errorCount = 0;
    int start = -1;
    for (int i = 0; i <= text.length(); i++) {
      char c = i == text.length() ? 0 : text.charAt(i);
      if (i == text.length() || isRepresentable(c, charset)) {
        if (start != -1) {
          TextRange range = new TextRange(start, i);
          String message = InspectionsBundle.message("unsupported.character.for.the.charset", charset);
          ProblemDescriptor descriptor =
            manager.createProblemDescriptor(file, range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, CHANGE_ENCODING_FIX);
          descriptors.add(descriptor);
          start = -1;
          //do not report too many errors
          if (errorCount++ > 200) break;
        }
      }
      else if (start == -1) {
        start = i;
      }
    }
  }

  private static boolean isRepresentable(final char c, final Charset charset) {
    String str = Character.toString(c);
    ByteBuffer out = charset.encode(str);
    CharBuffer buffer = charset.decode(out);
    return str.equals(buffer.toString());
  }

  private static class ReloadInAnotherEncodingFix extends ChangeEncodingFix {
    @NotNull
    @Override
    public String getName() {
      return "Reload in another encoding";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (FileDocumentManager.getInstance().isFileModified(descriptor.getPsiElement().getContainingFile().getVirtualFile())) return;
      super.applyFix(project, descriptor);
    }
  }

  private static class ChangeEncodingFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Change file encoding";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      ChooseFileEncodingAction action = new ChooseFileEncodingAction(virtualFile) {
        @Override
        protected void chosen(VirtualFile virtualFile, Charset charset) {
          if (virtualFile != null) {
            EncodingManager.getInstance().setEncoding(virtualFile, charset);
          }
        }
      };
      DefaultActionGroup group = action.createGroup(false);
      DataContext dataContext = DataManager.getInstance().getDataContext();
      JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext, false, false, false, null, 30, null).showInBestPositionFor(dataContext);
    }
  }
}
