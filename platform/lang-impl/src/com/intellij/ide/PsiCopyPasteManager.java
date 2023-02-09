// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.dnd.LinuxDragAndDropSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public final class PsiCopyPasteManager {
  public static PsiCopyPasteManager getInstance() {
    return ApplicationManager.getApplication().getService(PsiCopyPasteManager.class);
  }

  private static final Logger LOG = Logger.getInstance(PsiCopyPasteManager.class);

  private MyData myRecentData;
  private final CopyPasteManagerEx myCopyPasteManager;

  public PsiCopyPasteManager() {
    myCopyPasteManager = CopyPasteManagerEx.getInstanceEx();
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (myRecentData != null && (!myRecentData.isValid() || myRecentData.project == project)) {
          myRecentData = null;
        }

        Transferable[] contents = myCopyPasteManager.getAllContents();
        for (int i = contents.length - 1; i >= 0; i--) {
          Transferable t = contents[i];
          if (t instanceof MyTransferable) {
            MyData myData = ((MyTransferable)t).myDataProxy;
            if (!myData.isValid() || myData.project == project) {
              myCopyPasteManager.removeContent(t);
            }
          }
        }
      }
    });
  }

  public PsiElement @Nullable [] getElements(boolean[] isCopied) {
    MyData data = myCopyPasteManager.getContents(ourDataFlavor);
    if (data == null || !Comparing.equal(data, myRecentData)) {
      return null;
    }
    if (isCopied != null) {
      isCopied[0] = myRecentData.isCopied;
    }
    return myRecentData.getElements();
  }

  public static @NotNull Transferable newTransferable(PsiElement @NotNull ... element) {
    return new MyTransferable(element);
  }

  public static PsiElement @Nullable [] getElements(@Nullable Transferable content) {
    MyData data = getData(content);
    return data == null ? null : data.getElements();
  }

  private static @Nullable MyData getData(@Nullable Transferable content) {
    Object transferData;
    try {
      transferData = content == null ? null : content.getTransferData(ourDataFlavor);
    }
    catch (UnsupportedFlavorException | InvalidDnDOperationException | IOException e) {
      return null;
    }
    return transferData instanceof MyData ? (MyData)transferData : null;
  }

  public void clear() {
    myRecentData = null;
    myCopyPasteManager.setContents(new StringSelection(""));
  }

  public void setElements(PsiElement @NotNull [] elements, boolean copied) {
    myRecentData = new MyData(elements, copied);
    myCopyPasteManager.setContents(new MyTransferable(myRecentData));
  }

  public boolean isCutElement(Object element) {
    if (myRecentData == null) return false;
    if (myRecentData.isCopied) return false;
    PsiElement[] elements = myRecentData.getElements();
    if (elements == null) return false;
    return ArrayUtil.contains(element, elements);
  }

  private static final DataFlavor ourDataFlavor;

  static {
    try {
      final Class<MyData> flavorClass = MyData.class;
      final Thread currentThread = Thread.currentThread();
      final ClassLoader currentLoader = currentThread.getContextClassLoader();
      try {
        currentThread.setContextClassLoader(flavorClass.getClassLoader());
        ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + flavorClass.getName());
      }
      finally {
        currentThread.setContextClassLoader(currentLoader);
      }
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }


  static class MyData {
    final Project project;
    final boolean isCopied;
    final List<SmartPsiElementPointer<?>> pointers = new ArrayList<>();
    final String names;
    final List<File> files;

    MyData(PsiElement @NotNull [] elements, boolean copied) {
      project = elements.length == 0 ? null : elements[0].getProject();
      for (PsiElement element : elements) {
        pointers.add(SmartPointerManager.createPointer(element));
      }
      isCopied = copied;

      names = StringUtil.nullize(
        Stream.of(elements)
          .filter(PsiNamedElement.class::isInstance)
          .map(e -> StringUtil.nullize(((PsiNamedElement)e).getName(), true))
          .filter(Objects::nonNull)
          .collect(Collectors.joining("\n")));

      files = asFileList(elements);
    }

    public PsiElement[] getElements() {
      return ReadAction.compute(() -> {
        List<PsiElement> result = new ArrayList<>();
        for (SmartPsiElementPointer<?> pointer : pointers) {
          PsiElement element = pointer.getElement();
          if (element != null) {
            result.add(element);
          }
        }
        return result.toArray(PsiElement.EMPTY_ARRAY);
      });
    }

    public boolean isValid() {
      if (pointers.isEmpty()) {
        return false;
      }
      SmartPsiElementPointer<?> pointer = pointers.get(0);
      VirtualFile virtualFile = pointer.getVirtualFile();
      Project expectedProject = virtualFile == null ? null : ProjectLocator.getInstance().guessProjectForFile(virtualFile);
      if (expectedProject != null && expectedProject != project) {
        // files must have moved between projects, everything is invalid, and pointer.getElement() would likely crash
        return false;
      }
      return pointer.getElement() != null;
    }

    void fileMovedOutsideProject(@NotNull VirtualFile file) {
      pointers.removeIf(pointer -> file.equals(pointer.getVirtualFile()));
    }
  }

  /** @deprecated Use {{@link #getElements(Transferable)} and {@link #newTransferable(PsiElement...)} instead */
  @Deprecated
  @ApiStatus.Internal
  public static class MyTransferable implements Transferable {
    private static final DataFlavor[] DATA_FLAVORS_COPY = {
      ourDataFlavor, DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor,
      LinuxDragAndDropSupport.uriListFlavor, LinuxDragAndDropSupport.gnomeFileListFlavor
    };
    private static final DataFlavor[] DATA_FLAVORS_CUT = {
      ourDataFlavor, DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor,
      LinuxDragAndDropSupport.uriListFlavor, LinuxDragAndDropSupport.gnomeFileListFlavor, LinuxDragAndDropSupport.kdeCutMarkFlavor
    };

    private final @NotNull MyData myDataProxy;

    MyTransferable(@NotNull MyData data) {
      myDataProxy = data;
    }

    public MyTransferable(PsiElement @NotNull [] selectedValues) {
      this(new PsiCopyPasteManager.MyData(selectedValues, true));
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      Object result = getTransferDataOrNull(flavor);
      if (result == null) throw new IOException();
      return result;
    }

    private @Nullable Object getTransferDataOrNull(DataFlavor flavor) throws UnsupportedFlavorException {
      if (ourDataFlavor.equals(flavor)) {
        return myDataProxy;
      }
      else if (DataFlavor.stringFlavor.equals(flavor)) {
        return myDataProxy.names;
      }
      else if (DataFlavor.javaFileListFlavor.equals(flavor)) {
        return myDataProxy.files;
      }
      else if (flavor.equals(LinuxDragAndDropSupport.uriListFlavor)) {
        List<File> files = myDataProxy.files;
        return files == null ? null : LinuxDragAndDropSupport.toUriList(files);
      }
      else if (flavor.equals(LinuxDragAndDropSupport.gnomeFileListFlavor)) {
        List<File> files = myDataProxy.files;
        if (files == null) return null;
        String string = (myDataProxy.isCopied ? "copy\n" : "cut\n") + LinuxDragAndDropSupport.toUriList(files);
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
      }
      else if (flavor.equals(LinuxDragAndDropSupport.kdeCutMarkFlavor) && !myDataProxy.isCopied) {
        return new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8));
      }
      throw new UnsupportedFlavorException(flavor);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      DataFlavor[] flavors = myDataProxy.isCopied ? DATA_FLAVORS_COPY : DATA_FLAVORS_CUT;
      return JBIterable.of(flavors).filter(flavor -> {
        try {
          return getTransferDataOrNull(flavor) != null;
        }
        catch (UnsupportedFlavorException ex) {
          return false;
        }
      }).toList().toArray(new DataFlavor[0]);
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return ArrayUtilRt.find(getTransferDataFlavors(), flavor) != -1;
    }

    public PsiElement[] getElements() {
      return myDataProxy.getElements();
    }
  }

  public static @Nullable List<File> asFileList(PsiElement[] elements) {
    List<File> result = new ArrayList<>();
    for (PsiElement element : elements) {
      VirtualFile vFile = asVirtualFile(element);
      if (vFile != null && vFile.getFileSystem() instanceof LocalFileSystem) {
        result.add(new File(vFile.getPath()));
      }
    }
    return result.isEmpty() ? null : result;
  }

  public static @Nullable VirtualFile asVirtualFile(@Nullable PsiElement element) {
    PsiFileSystemItem psiFile = null;
    if (element instanceof PsiFileSystemItem) {
      psiFile = (PsiFileSystemItem)element;
    }
    else if (element instanceof PsiDirectoryContainer) {
      PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
      if (directories.length == 0) {
        LOG.error("No directories for " + element + " of " + element.getClass());
        return null;
      }
      psiFile = directories[0];
    }
    else if (element != null) {
      psiFile = element.getContainingFile();
    }
    if (psiFile != null) {
      return psiFile.getVirtualFile();
    }
    return null;
  }

  public static final class EscapeHandler extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent event) {
      if (event.isConsumed()) return; // already processed
      if (0 != event.getModifiers()) return; // modifier pressed
      if (KeyEvent.VK_ESCAPE != event.getKeyCode()) return; // not ESC
      boolean[] copied = new boolean[1];
      PsiCopyPasteManager manager = getInstance();
      if (manager.getElements(copied) == null) return; // no copied element
      if (copied[0]) return; // nothing is copied
      manager.clear();
      event.consume();
    }
  }

  @ApiStatus.Internal
  public void fileMovedOutsideProject(@NotNull VirtualFile file) {
    if (myRecentData != null) {
      myRecentData.fileMovedOutsideProject(file);
    }
    myCopyPasteManager.removeIf(transferable -> transferable instanceof MyTransferable && !((MyTransferable)transferable).myDataProxy.isValid());
  }
}