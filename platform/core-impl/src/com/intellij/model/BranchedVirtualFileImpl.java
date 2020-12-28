// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.AbstractFileViewProvider;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

final class BranchedVirtualFileImpl extends BranchedVirtualFile {
  private final @NotNull ModelBranchImpl myBranch;
  private final boolean myDirectory;
  private @Nullable VirtualFile myOriginal;
  private BranchedVirtualFileImpl myChangedParent;
  private Ref<BranchedVirtualFileImpl[]> myChangedChildren;
  private byte[] myByteContent;

  BranchedVirtualFileImpl(@NotNull ModelBranchImpl branch,
                          @Nullable VirtualFile original,
                          @NotNull String name,
                          boolean isDirectory,
                          @Nullable BranchedVirtualFileImpl parent) {
    super(original, name);
    myChangedParent = parent;
    putUserData(AbstractFileViewProvider.FREE_THREADED, true);
    this.myOriginal = original;
    myBranch = branch;
    myDirectory = isDirectory;
  }

  @Override
  @NotNull
  protected ModelBranchImpl getBranch() {
    return myBranch;
  }

  @Override
  public void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    throw new UnsupportedOperationException("Branch files shouldn't be modified");
  }

  @Override
  public boolean isDirectory() {
    return myDirectory;
  }


  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    if (isDirectory()) throw new IOException("Cannot write a directory: " + this);
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      @Override
      public void close() {
        assert isWritable();

        setModificationStamp(newModificationStamp);
        myByteContent = toByteArray();
      }
    }, this);
  }

  @Override
  public @NotNull CharSequence getContent() {
    if (isDirectory()) throw new IllegalStateException("Cannot get content of directory: " + this);
    if (!getFileType().isBinary()) {
      if (myOriginal != null) {
        FileViewProvider vp = PsiManagerEx.getInstanceEx(myBranch.getProject()).getFileManager().findViewProvider(myOriginal);
        if (vp != null) {
          Document document = FileDocumentManager.getInstance().getCachedDocument(myOriginal);
          if (document != null && PsiDocumentManager.getInstance(myBranch.getProject()).isUncommited(document)) {
            throw new IllegalStateException("Content loading is only allowed for committed original files");
          }
          return vp.getContents().toString();
        }
      }
      try {
        return LoadTextUtil.getTextByBinaryPresentation(contentsToByteArray(), this);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    throw new UnsupportedOperationException("No string content for binary file " + this);
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    if (isDirectory()) throw new IOException("Cannot get content of directory: " + this);
    if (myByteContent != null) return myByteContent.clone();
    if (myOriginal == null) return ArrayUtil.EMPTY_BYTE_ARRAY;
    return myOriginal.contentsToByteArray();
  }

  @Override
  public void rename(Object requestor, @NotNull String newName) throws IOException {
    super.rename(requestor, newName);
    BranchedVirtualFileImpl parent = getParent();
    if (parent != null && parent.myChangedChildren == null) {
      // findChild must search over changed children names, so we need to track children now
      parent.myChangedChildren = Ref.create(parent.getChildren());
    }
    myBranch.addVfsStructureChange(this);
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile _newParent) {
    assert ModelBranch.getFileBranch(_newParent) == myBranch;
    BranchedVirtualFileImpl newParent = (BranchedVirtualFileImpl)_newParent;
    BranchedVirtualFileImpl oldParent = getParent();

    if (oldParent == null) {
      throw new UnsupportedOperationException("Unable to move root directory");
    }
    if (newParent.equals(oldParent)) return;

    myChangedParent = newParent;

    oldParent.myChangedChildren = Ref.create(ArrayUtil.remove(oldParent.getChildren(), this));
    BranchedVirtualFileImpl newFile = isDirectory() ?
                                      new BranchedVirtualFileImpl(myBranch, myOriginal, getName(), true, newParent) : this;
    newParent.myChangedChildren = Ref.create(ArrayUtil.insert(newParent.getChildren(), 0, newFile));

    myBranch.addVfsStructureChange(this);
  }

  @Override
  public @Nullable VirtualFile findChild(@NotNull String name) {
    if (myChangedChildren != null) {
      BranchedVirtualFileImpl[] array = myChangedChildren.get();
      return array == null ? null : ContainerUtil.find(array, f -> name.equals(f.getName()));
    }
    assert myOriginal != null;
    VirtualFile child = myOriginal.findChild(name);
    VirtualFile copy = child == null ? null : myBranch.findFileCopy(child);
    return copy != null && copy.isValid() && equals(copy.getParent()) && name.equals(copy.getName()) ? copy : null;
  }

  @Override
  public BranchedVirtualFileImpl getParent() {
    if (myChangedParent != null) return myChangedParent;
    assert myOriginal != null;
    VirtualFile parent = myOriginal.getParent();
    return parent == null ? null : myBranch.findPhysicalFileCopy(parent);
  }

  @Override
  public BranchedVirtualFileImpl[] getChildren() {
    if (myChangedChildren != null) {
      BranchedVirtualFileImpl[] array = myChangedChildren.get();
      return array == null || array.length == 0 ? array : array.clone();
    }

    assert myOriginal != null;
    myBranch.assertAllChildrenLoaded(myOriginal);
    VirtualFile[] baseChildren = myOriginal.getChildren();
    if (baseChildren == null) return null;

    return ContainerUtil.map2Array(baseChildren, BranchedVirtualFileImpl.class, f -> Objects.requireNonNull(myBranch.findPhysicalFileCopy(f)));
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) {
    return createChild(name, true);
  }

  @Override
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) {
    return createChild(name, false);
  }

  private VirtualFile createChild(String name, boolean isDirectory) {
    BranchedVirtualFileImpl created = new BranchedVirtualFileImpl(myBranch, null, name, isDirectory, this);
    myChangedChildren = Ref.create(ArrayUtil.insert(getChildren(), 0, created));
    created.myChangedChildren = Ref.create(isDirectory ? new BranchedVirtualFileImpl[0] : null);
    myBranch.addVfsStructureChange(created);
    return created;
  }

  @Nullable
  VirtualFile getOriginal() {
    return myOriginal;
  }

  @NotNull
  VirtualFile getOrCreateOriginal() throws IOException {
    VirtualFile result = this.myOriginal;
    if (result == null) {
      myOriginal = result = createFile();
    }
    return result;
  }

  @NotNull
  private VirtualFile createFile() throws IOException {
    String name = getName();
    VirtualFile originalParent = getParent().getOrCreateOriginal();
    VirtualFile original = isDirectory()
                           ? originalParent.createChildDirectory(this, name)
                           : originalParent.createChildData(this, name);
    if (!isDirectory()) {
      original.setBinaryContent(contentsToByteArray());
    }
    return original;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof BranchedVirtualFileImpl)) return false;
    return myOriginal != null && myBranch.equals(((BranchedVirtualFileImpl)obj).myBranch) &&
           myOriginal.equals(((BranchedVirtualFileImpl)obj).myOriginal);
  }

  @Override
  public String toString() {
    return "BranchedVFile[" + myBranch.hashCode() + "]: " + getPresentableUrl();
  }
}
