/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.ide.DataManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class LossyEncodingInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.LossyEncodingInspection");

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
    FileViewProvider viewProvider = file.getViewProvider();
    if (viewProvider.getBaseLanguage() != file.getLanguage()) return null;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    if (!virtualFile.isInLocalFileSystem()) return null;
    CharSequence text = viewProvider.getContents();
    Charset charset = LoadTextUtil.extractCharsetFromFileContent(file.getProject(), virtualFile, text);

    // no sense in checking transparently decoded file: all characters there are already safely encoded
    if (charset instanceof Native2AsciiCharset) return null;

    List<ProblemDescriptor> descriptors = new SmartList<>();
    boolean ok = checkFileLoadedInWrongEncoding(file, manager, isOnTheFly, virtualFile, charset, descriptors);
    if (ok) {
      checkIfCharactersWillBeLostAfterSave(file, manager, isOnTheFly, text, charset, descriptors);
    }

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static boolean checkFileLoadedInWrongEncoding(@NotNull PsiFile file,
                                                        @NotNull InspectionManager manager,
                                                        boolean isOnTheFly,
                                                        @NotNull VirtualFile virtualFile,
                                                        @NotNull Charset charset,
                                                        @NotNull List<ProblemDescriptor> descriptors) {
    if (FileDocumentManager.getInstance().isFileModified(virtualFile) // when file is modified, it's too late to reload it
        || EncodingUtil.checkCanReload(virtualFile).second != null // can't reload in another encoding, no point trying
      ) {
      return true;
    }
    if (!isGoodCharset(virtualFile, charset)) {
      descriptors.add(manager.createProblemDescriptor(file, "File was loaded in the wrong encoding: '"+charset+"'",
                                                      RELOAD_ENCODING_FIX, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
      return false;
    }
    return true;
  }

  // check if file was loaded in correct encoding
  // returns true if text converted with charset is equals to the bytes currently on disk
  private static boolean isGoodCharset(@NotNull VirtualFile virtualFile, @NotNull Charset charset) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return true;
    byte[] loadedBytes;
    byte[] bytesToSave;
    try {
      loadedBytes = virtualFile.contentsToByteArray();
      bytesToSave = new String(loadedBytes, charset).getBytes(charset);
    }
    catch (Exception e) {
      return true;
    }
    if (loadedBytes.length == 0 && bytesToSave.length == 0) {
      // hold on, file was just created, no content was written yet
      return true;
    }
    byte[] bom = virtualFile.getBOM();
    if (bom != null && !ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave); // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    boolean equals = Arrays.equals(bytesToSave, loadedBytes);
    if (!equals && LOG.isDebugEnabled()) {
      try {
        String tempDir = FileUtil.getTempDirectory();
        FileUtil.writeToFile(new File(tempDir, "lossy-bytes-to-save"), bytesToSave);
        FileUtil.writeToFile(new File(tempDir, "lossy-loaded-bytes"), loadedBytes);
        LOG.debug("lossy bytes dumped into " + tempDir);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return equals;
  }

  private static void checkIfCharactersWillBeLostAfterSave(@NotNull PsiFile file,
                                                           @NotNull InspectionManager manager,
                                                           boolean isOnTheFly,
                                                           @NotNull CharSequence text,
                                                           @NotNull Charset charset,
                                                           @NotNull List<ProblemDescriptor> descriptors) {
    int errorCount = 0;
    int start = -1;
    CharBuffer buffer = CharBuffer.wrap(text); // temp buffer for encoding/decoding back a char or a surrogate pair.
    for (int i = 0; i <= text.length(); i++) {
      char c = i >= text.length() ? 0 : text.charAt(i);
      int end = Character.isHighSurrogate(c) && i<text.length()-1 ? i + 2 : i+1;
      if (i == text.length() || isRepresentable(buffer, i, end, charset)) {
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
      if (end != i+1) {
        i++; // skip surrogate low
      }
    }
  }

  private static boolean isRepresentable(@NotNull CharBuffer srcBuffer,
                                         int start,
                                         int end,
                                         @NotNull Charset charset) {
    srcBuffer.position(start);
    srcBuffer.limit(end);
    ByteBuffer out = charset.encode(srcBuffer);
    CharBuffer buffer = charset.decode(out);
    srcBuffer.position(start);
    return buffer.equals(srcBuffer);
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
    public String getFamilyName() {
      return "Change file encoding";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();

      Editor editor = PsiUtilBase.findEditor(psiFile);
      DataContext dataContext = createDataContext(editor, editor == null ? null : editor.getComponent(), virtualFile, project);
      ListPopup popup = new ChangeFileEncodingAction().createPopup(dataContext);
      if (popup != null) {
        popup.showInBestPositionFor(dataContext);
      }
    }

    @NotNull
    static DataContext createDataContext(Editor editor, Component component, VirtualFile selectedFile, Project project) {
      DataContext parent = DataManager.getInstance().getDataContext(component);
      DataContext context = SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT.getName(), editor == null ? null : editor.getComponent(), parent);
      DataContext projectContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT.getName(), project, context);
      return SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE.getName(), selectedFile, projectContext);
    }
  }
}
