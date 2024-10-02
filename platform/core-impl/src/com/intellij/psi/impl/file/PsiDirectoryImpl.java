// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.core.CoreBundle;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class PsiDirectoryImpl extends PsiElementBase implements PsiDirectory, Queryable {
  private static final Key<Boolean> UPDATE_ADDED_FILE_KEY = Key.create("UPDATE_ADDED_FILE_KEY");
  private static final Logger LOG = Logger.getInstance(PsiDirectoryImpl.class);

  private final PsiManagerImpl myManager;
  private final VirtualFile myFile;

  public PsiDirectoryImpl(@NotNull PsiManagerImpl manager, @NotNull VirtualFile file) {
    myManager = manager;
    myFile = file;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && !getProject().isDisposed();
  }

  @Override
  public @NotNull Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public @NotNull PsiManager getManager() {
    return myManager;
  }

  @Override
  public @NotNull String getName() {
    return myFile.getName();
  }

  @Override
  public @NotNull PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);

    try {
      myFile.rename(myManager, name);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }

    return this;
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) {
      throw new IncorrectOperationException(CoreBundle.message("cannot.rename.root.directory", myFile.getPath()));
    }
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(myFile)) {
      throw new IncorrectOperationException(CoreBundle.message("dir.already.exists.error", child.getPresentableUrl()));
    }
  }

  @Override
  public PsiDirectory getParentDirectory() {
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    if (!parentFile.isValid()) {
      LOG.error("Invalid parent: " + parentFile + " of dir " + myFile + ", dir.valid=" + myFile.isValid());
      return null;
    }
    return myManager.findDirectory(parentFile);
  }

  @Override
  public PsiDirectory @NotNull [] getSubdirectories() {
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiDirectory> dirs = new ArrayList<>();
    for (VirtualFile file : files) {
      PsiDirectory dir = myManager.findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    return dirs.toArray(PsiDirectory.EMPTY_ARRAY);
  }

  @Override
  public PsiFile @NotNull [] getFiles() {
    return getFilesImpl(null);
  }

  @Override
  public PsiFile @NotNull [] getFiles(@NotNull GlobalSearchScope scope) {
    return getFilesImpl(scope);
  }

  private PsiFile @NotNull [] getFilesImpl(@Nullable GlobalSearchScope scope) {
    if (!myFile.isValid()) throw new InvalidVirtualFileAccessException(myFile);
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiFile> psiFiles = new ArrayList<>();
    for (VirtualFile file : files) {
      // The scope allows us to pre-filter the virtual files and avoid creating unnecessary PSI files.
      if (scope != null && !scope.contains(file)) continue;
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return PsiUtilCore.toPsiFileArray(psiFiles);
  }

  @Override
  public PsiDirectory findSubdirectory(@NotNull String name) {
    ProgressManager.checkCanceled();
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findDirectory(childVFile);
  }

  @Override
  public PsiFile findFile(@NotNull String name) {
    ProgressManager.checkCanceled();
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    if (!childVFile.isValid()) {
      LOG.error(
        "Invalid file: " + childVFile + " in dir " + myFile + ", dir.valid=" + myFile.isValid(),
        new InvalidVirtualFileAccessException(childVFile)
      );
      return null;
    }
    return myManager.findFile(childVFile);
  }

  @Override
  public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
    checkValid();

    for (VirtualFile vFile : myFile.getChildren()) {
      ProgressManager.checkCanceled();
      if (!vFile.isValid()) continue;

      boolean isDir = vFile.isDirectory();
      if (processor instanceof PsiFileSystemItemProcessor && !((PsiFileSystemItemProcessor)processor).acceptItem(vFile.getName(), isDir)) {
        continue;
      }

      PsiFileSystemItem item = isDir ? myManager.findDirectory(vFile) : myManager.findFile(vFile);
      if (item != null && !processor.execute(item)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    checkValid();

    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiElement> children = new ArrayList<>(files.length);
    processChildren(element -> {
      children.add(element);
      return true;
    });

    return PsiUtilCore.toPsiElementArray(children);
  }

  private void checkValid() {
    if (!isValid()) {
      throw new PsiInvalidElementAccessException(this);
    }
  }

  @Override
  public PsiDirectory getParent() {
    return getParentDirectory();
  }

  @Override
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public int getTextLength() {
    return -1;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return -1;
  }

  @Override
  public String getText() {
    return "";
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return ArrayUtilRt.EMPTY_CHAR_ARRAY;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public final boolean isWritable() {
    return myFile.isWritable();
  }

  @Override
  public boolean isPhysical() {
    return !(myFile.getFileSystem() instanceof NonPhysicalFileSystem) && !myFile.getFileSystem().getProtocol().equals("temp");
  }

  @Override
  public PsiElement copy() {
    throw new IncorrectOperationException();
  }

  @Override
  public @NotNull PsiDirectory createSubdirectory(@NotNull String name) throws IncorrectOperationException {
    checkCreateSubdirectory(name);

    try {
      VirtualFile file = getVirtualFile().createChildDirectory(myManager, name);
      PsiDirectory directory = myManager.findDirectory(file);
      if (directory == null) throw new IncorrectOperationException("Cannot find directory in '" + file.getPresentableUrl() + "'");
      return directory;
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  @Override
  public void checkCreateSubdirectory(@NotNull String name) throws IncorrectOperationException {
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(CoreBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull String name) throws IncorrectOperationException {
    checkCreateFile(name);

    try {
      VirtualFile vFile = getVirtualFile().createChildData(myManager, name);
      PsiFile psiFile = myManager.findFile(vFile);
      assert psiFile != null : vFile.getPath();
      return psiFile;
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e);
    }
  }

  @Override
  public @NotNull PsiFile copyFileFrom(@NotNull String newName, @NotNull PsiFile originalFile) throws IncorrectOperationException {
    checkCreateFile(newName);

    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(originalFile);
    if (document != null) {
      FileDocumentManager.getInstance().saveDocument(document);
    }

    VirtualFile parent = getVirtualFile();
    try {
      VirtualFile vFile = originalFile.getVirtualFile();
      if (vFile == null) throw new IncorrectOperationException("Cannot copy non-physical file: " + originalFile);

      VirtualFile copyVFile;
      if (parent.getFileSystem() == vFile.getFileSystem()) {
        copyVFile = vFile.copy(this, parent, newName);
      }
      else if (vFile instanceof LightVirtualFile) {
        copyVFile = parent.createChildData(this, newName);
        copyVFile.setBinaryContent(vFile.contentsToByteArray());
      }
      else {
        copyVFile = VfsUtilCore.copyFile(this, vFile, parent, newName);
      }
      if (UPDATE_ADDED_FILE_KEY.get(this, true)) {
        DumbService.getInstance(getProject()).completeJustSubmittedTasks();
        PsiFile copyPsi = findCopy(copyVFile, vFile);
        UpdateAddedFileProcessor.updateAddedFiles(Collections.singletonList(copyPsi), Collections.singletonList(originalFile));
        return copyPsi;
      }
      return findCopy(copyVFile, vFile);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e);
    }
  }

  private @NotNull PsiFile findCopy(VirtualFile copyVFile, VirtualFile vFile) {
    PsiFile copyPsi = myManager.findFile(copyVFile);
    if (copyPsi == null) throw new IncorrectOperationException("Could not find file " + copyVFile + " after copying " + vFile);
    return copyPsi;
  }

  public <T extends Throwable> void executeWithUpdatingAddedFilesDisabled(ThrowableRunnable<T> runnable) throws T {
    try {
      putUserData(UPDATE_ADDED_FILE_KEY, false);
      runnable.run();
    }
    finally {
      putUserData(UPDATE_ADDED_FILE_KEY,null);
    }
  }

  @Override
  public void checkCreateFile(@NotNull String name) throws IncorrectOperationException {
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(CoreBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    checkAdd(element);

    if (element instanceof PsiFile) {
      PsiFile originalFile = (PsiFile)element;

      try {
        VirtualFile newVFile;
        if (originalFile instanceof PsiFileImpl) {
          newVFile = myFile.createChildData(myManager, originalFile.getName());
          String text = originalFile.getText();
          PsiFile psiFile = getManager().findFile(newVFile);
          Document document = psiFile == null ? null : psiFile.getViewProvider().getDocument();
          FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
          if (document != null) {
            document.setText(text);
            if (psiFile.isPhysical()) {
              fileDocumentManager.saveDocument(document);
            }
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
          }
          else {
            String lineSeparator = fileDocumentManager.getLineSeparator(newVFile, getProject());
            if (!lineSeparator.equals("\n")) {
              text = StringUtil.convertLineSeparators(text, lineSeparator);
            }

            LoadTextUtil.write(getProject(), newVFile, myManager, text, -1);
          }
        }
        else {
          byte[] storedContents = ((PsiBinaryFileImpl)originalFile).getStoredContents();
          if (storedContents != null) {
            newVFile = myFile.createChildData(myManager, originalFile.getName());
            newVFile.setBinaryContent(storedContents);
          }
          else {
            newVFile = VfsUtilCore.copyFile(null, originalFile.getVirtualFile(), myFile);
          }
        }

        PsiFile newFile = myManager.findFile(newVFile);
        if (newFile == null) throw new IncorrectOperationException("Could not find file " + newVFile);
        UpdateAddedFileProcessor.updateAddedFiles(Collections.singletonList(newFile), Collections.singletonList(originalFile));
        return newFile;
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e);
      }
    }

    throw new IncorrectOperationException(element + " (" + element.getClass() + ")");
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (element instanceof PsiDirectory || element instanceof PsiFile) {
      String name = ((PsiFileSystemItem)element).getName();
      boolean caseSensitive = getVirtualFile().isCaseSensitive();
      VirtualFile existing = ContainerUtil.find(getVirtualFile().getChildren(),
                                                item -> Comparing.strEqual(item.getName(), name, caseSensitive));
      if (existing != null) {
        throw new IncorrectOperationException(
          CoreBundle.message(existing.isDirectory() ? "dir.already.exists.error" : "file.already.exists.error",
                             existing.getPresentableUrl()));
      }
    }
    else {
      throw new IncorrectOperationException(element.getClass().getName());
    }
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    checkDelete();
    try {
      myFile.delete(myManager);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e);
    }
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkDelete(myFile);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDirectory(this);
  }

  @Override
  public String toString() {
    return "PsiDirectory:" + myFile.getPresentableUrl();
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public @Nullable NavigationRequest navigationRequest() {
    return NavigationRequest.directoryNavigationRequest(this);
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiNavigationSupport.getInstance().navigateToDirectory(this, requestFocus);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("fileName", getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PsiDirectoryImpl directory = (PsiDirectoryImpl)o;

    if (!myManager.equals(directory.myManager)) return false;
    if (!myFile.equals(directory.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myManager.hashCode();
    result = 31 * result + myFile.hashCode();
    return result;
  }
}