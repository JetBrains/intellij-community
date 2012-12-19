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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction;
import com.intellij.openapi.vfs.encoding.ReloadFileInOtherEncodingAction;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.wm.impl.status.EncodingActionsPair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
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

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("lossy.encoding");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "LossyEncoding";
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) return null;
    if (!file.isPhysical()) return null;
    if (file.getViewProvider().getBaseLanguage() != file.getLanguage()) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    if (virtualFile.getFileSystem() != LocalFileSystem.getInstance()
        // tests
        && virtualFile.getFileSystem() != TempFileSystem.getInstance()) return null;
    String text = file.getText();
    Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);

    // no sense in checking transparently decoded file: all characters there are already safely encoded
    if (charset instanceof Native2AsciiCharset) return null;

    List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();
    checkIfCharactersWillBeLostAfterSave(file, manager, isOnTheFly, text, charset, descriptors);
    checkFileLoadedInWrongEncoding(file, manager, isOnTheFly, text, virtualFile, charset, descriptors);

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static void checkFileLoadedInWrongEncoding(@NotNull PsiFile file,
                                                     @NotNull InspectionManager manager,
                                                     boolean isOnTheFly,
                                                     @NotNull String text,
                                                     @NotNull VirtualFile virtualFile,
                                                     @NotNull Charset charset,
                                                     @NotNull List<ProblemDescriptor> descriptors) {
    if (FileDocumentManager.getInstance().isFileModified(virtualFile) // when file is modified, it's too late to reload it
        || ChooseFileEncodingAction.checkCanReload(virtualFile).second != null // can't reload in another encoding, no point trying
      ) {
      return;
    }
    boolean ok = isGoodCharset(file.getProject(), virtualFile, text, charset);
    if (!ok) {
      descriptors.add(manager.createProblemDescriptor(file, "File was loaded in the wrong encoding: '"+charset+"'",
                                                      RELOAD_ENCODING_FIX, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
    }
  }

  // check if file was loaded in correct encoding
  // returns true if text converted with charset is equals to the bytes currently on disk
  public static boolean isGoodCharset(@NotNull Project project,
                                      @NotNull VirtualFile virtualFile,
                                      @NotNull String text,
                                      @NotNull Charset charset) {
    byte[] bytes;
    try {
      bytes = virtualFile.contentsToByteArray();
    }
    catch (IOException e) {
      return true;
    }
    String separator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, project);
    String toSave = StringUtil.convertLineSeparators(text, separator);
    byte[] bom = virtualFile.getBOM();
    bom = bom == null ? ArrayUtil.EMPTY_BYTE_ARRAY : bom;
    byte[] bytesToSave = toSave.getBytes(charset);
    if (!ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave); // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    return Arrays.equals(bytesToSave, bytes);
  }

  private static void checkIfCharactersWillBeLostAfterSave(@NotNull PsiFile file,
                                                           @NotNull InspectionManager manager,
                                                           boolean isOnTheFly,
                                                           @NotNull String text,
                                                           @NotNull Charset charset,
                                                           @NotNull List<ProblemDescriptor> descriptors) {
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

  private static boolean isRepresentable(final char c, @NotNull Charset charset) {
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

      Editor editor = PsiUtilBase.findEditor(psiFile);
      DataContext dataContext =
        EncodingActionsPair.createDataContext(editor, editor == null ? null : editor.getComponent(), virtualFile, project);
      ReloadFileInOtherEncodingAction reloadAction = new ReloadFileInOtherEncodingAction();
      reloadAction.actionPerformed(new AnActionEvent(null, dataContext, "", reloadAction.getTemplatePresentation(), ActionManager.getInstance(), 0));
    }
  }
}
