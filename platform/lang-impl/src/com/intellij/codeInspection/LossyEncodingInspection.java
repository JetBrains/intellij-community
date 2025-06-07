// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.encoding.EncodingUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class LossyEncodingInspection extends LocalInspectionTool {
  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.internationalization.issues");
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return "LossyEncoding";
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
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

    return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static boolean checkFileLoadedInWrongEncoding(@NotNull PsiFile file,
                                                        @NotNull InspectionManager manager,
                                                        boolean isOnTheFly,
                                                        @NotNull VirtualFile virtualFile,
                                                        @NotNull Charset charset,
                                                        @NotNull List<? super ProblemDescriptor> descriptors) {
    if (FileDocumentManager.getInstance().isFileModified(virtualFile) // when file is modified, it's too late to reload it
        || !EncodingUtil.canReload(virtualFile) // can't reload in another encoding, no point trying
      ) {
      return true;
    }
    if (!isGoodCharset(virtualFile, charset)) {
      LocalQuickFix[] fixes = getFixes(file, virtualFile, charset);
      descriptors.add(manager.createProblemDescriptor(file, LangBundle.message("inspection.lossy.encoding.description", charset), true,
                                                      ProblemHighlightType.GENERIC_ERROR, isOnTheFly, fixes));
      return false;
    }
    return true;
  }

  private static LocalQuickFix @NotNull [] getFixes(@NotNull PsiFile file,
                                                    @NotNull VirtualFile virtualFile,
                                                    @NotNull Charset wrongCharset) {
    Set<Charset> suspects = ContainerUtil.newHashSet(CharsetToolkit.getDefaultSystemCharset(), CharsetToolkit.getPlatformCharset());
    suspects.remove(wrongCharset);
    List<Charset> goodCharsets = ContainerUtil.filter(suspects, c -> isGoodCharset(virtualFile, c));
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (!goodCharsets.isEmpty()) {
      Charset goodCharset = goodCharsets.get(0);
      fixes.add(new LocalQuickFix() {
        @Override
        public @Nls @NotNull String getFamilyName() {
          return InspectionsBundle.message("reload.file.encoding.family.name", goodCharset.displayName());
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          Document document = PsiDocumentManager.getInstance(project).getDocument(file);
          if (document == null) {
            return;
          }
          ChangeFileEncodingAction.changeTo(project, document, null, virtualFile, goodCharset, EncodingUtil.Magic8.ABSOLUTELY, EncodingUtil.Magic8.ABSOLUTELY);
        }
      });
      fixes.add(new LocalQuickFix() {
        @Override
        public @Nls @NotNull String getFamilyName() {
          return InspectionsBundle.message("set.project.encoding.family.name", goodCharset.displayName());
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          EncodingProjectManager.getInstance(project).setDefaultCharsetName(goodCharset.name());
        }
      });
    }
    fixes.add(new ReloadInAnotherEncodingFix(file));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
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

    return Arrays.equals(bytesToSave, loadedBytes);
  }

  private static void checkIfCharactersWillBeLostAfterSave(@NotNull PsiFile file,
                                                           @NotNull InspectionManager manager,
                                                           boolean isOnTheFly,
                                                           @NotNull CharSequence text,
                                                           @NotNull Charset charset,
                                                           @NotNull List<ProblemDescriptor> descriptors) {
    //do not report too many errors
    for (int pos = 0, errorCount = 0; pos < text.length() && errorCount < 200; errorCount++) {
      TextRange errRange = CharsetUtil.findUnmappableCharacters(text.subSequence(pos, text.length()), charset);
      if (errRange == null) break;
      errRange = errRange.shiftRight(pos);

      ProblemDescriptor lastDescriptor = ContainerUtil.getLastItem(descriptors);
      if (lastDescriptor != null && lastDescriptor.getTextRangeInElement().getEndOffset() == errRange.getStartOffset()) {
        // combine two adjacent descriptors
        errRange = lastDescriptor.getTextRangeInElement().union(errRange);
        descriptors.remove(descriptors.size() - 1);
      }
      String message = InspectionsBundle.message("unsupported.character.for.the.charset", charset);
      ProblemDescriptor descriptor =
            manager.createProblemDescriptor(file, errRange, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
                                            new ChangeEncodingFix(file));
      descriptors.add(descriptor);
      pos = errRange.getEndOffset();
    }
  }

  private static final class ReloadInAnotherEncodingFix extends ChangeEncodingFix {
    ReloadInAnotherEncodingFix(@NotNull PsiFile file) {
      super(file);
    }

    @Override
    public @NotNull String getText() {
      return InspectionsBundle.message("reload.in.another.encoding.text");
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile psiFile,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      if (FileDocumentManager.getInstance().isFileModified(psiFile.getVirtualFile())) return;
      super.invoke(project, psiFile, editor, startElement, endElement);
    }
  }

  private static class ChangeEncodingFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    ChangeEncodingFix(@NotNull PsiFile file) {
      super(file);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionsBundle.message("change.encoding.fix.family.name");
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile psiFile,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      DataContext dataContext = createDataContext(project, editor, virtualFile);
      ListPopup popup = new ChangeFileEncodingAction().createPopup(dataContext, null);
      if (popup != null) {
        popup.showInBestPositionFor(dataContext);
      }
    }

    static @NotNull DataContext createDataContext(@NotNull Project project, @Nullable Editor editor, @Nullable VirtualFile selectedFile) {
      DataContext parent = editor == null ? null : DataManager.getInstance().getDataContext(editor.getContentComponent());
      return SimpleDataContext.builder()
        .setParent(parent)
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.VIRTUAL_FILE, selectedFile)
        .build();
    }
  }
}