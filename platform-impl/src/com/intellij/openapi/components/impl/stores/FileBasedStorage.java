package com.intellij.openapi.components.impl.stores;


import com.intellij.Patches;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.ArrayUtil;
import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final IFile myFile;
  protected final String myRootElementName;
  private long myInitialFileTimestamp;
  private static final byte[] BUFFER = new byte[10];

  public FileBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroManager,
                          StreamProvider streamProvider,
                          final String filePath,
                          final String fileSpec,
                          String rootElementName,
                          Disposable parentDisposable,
                          PicoContainer picoContainer,
                          ComponentRoamingManager componentRoamingManager) {
    super(pathMacroManager, parentDisposable, rootElementName, streamProvider,  fileSpec, componentRoamingManager);

    myRootElementName = rootElementName;
    myFilePath = filePath;
    myFile = FILE_SYSTEM.createFile(myFilePath);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);


    if (myFile.exists()) {
      myInitialFileTimestamp = myFile.getTimeStamp();
    }

    if (virtualFileTracker != null && messageBus != null) {
      final String path = myFile.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL + "://" + path.replace(File.separatorChar, '/');


      final Listener listener = messageBus.syncPublisher(StateStorage.STORAGE_TOPIC);
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        public void contentsChanged(final VirtualFileEvent event) {
          listener.storageFileChanged(event, FileBasedStorage.this);
        }
      }, true, this);
    }
  }

  protected MySaveSession createSaveSession(final XmlElementStorage.MyExternalizationSession externalizationSession) {
    return new FileSaveSession(externalizationSession);
  }

  protected class FileSaveSession extends MySaveSession {
    public FileSaveSession(MyExternalizationSession externalizationSession) {
      super(externalizationSession);
    }

    @Override
    protected boolean phisicalContentNeedsSave() {
      if (!myFile.exists()) return true;

      final byte[] text = StorageUtil.printDocument(getDocumentToSave());

      try {
        return !Arrays.equals(myFile.loadBytes(), text);
      }
      catch (IOException e) {
        LOG.debug(e);
        return true;
      }
    }

    @Override
    protected Integer calcHash() {
      int hash = myStorageData.getHash();

      if (myPathMacroSubstitutor != null) {
        hash = 31*hash + myPathMacroSubstitutor.hashCode();
      }
      return hash;
    }

    protected void doSave() throws StateStorageException {
      final byte[] text = StorageUtil.printDocument(getDocumentToSave());

      StorageUtil.save(myFile, text);
    }

    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      boolean needsSave = needsSave();
      if (needsSave) {
        if (LOG.isDebugEnabled()) {
          LOG.info("File " + myFileSpec + " needs save; hash=" + myUpToDateHash + "; currentHash=" + calcHash() + "; content needs save=" + phisicalContentNeedsSave());          
        }
        return getAllStorageFiles();
      }
      else {
        return Collections.emptyList();
      }
    }

    public List<IFile> getAllStorageFiles() {
      return Collections.singletonList(myFile);
    }

  }


  protected void loadState(final StorageData result, final Element element) throws StateStorageException {
    ((FileStorageData)result).myFileName = myFile.getAbsolutePath();
    ((FileStorageData)result).myFilePath = myFile.getAbsolutePath();
    super.loadState(result, element);
  }

  @NotNull
  protected StorageData createStorageData() {
    return new FileStorageData(myRootElementName);
  }

  public static class FileStorageData extends StorageData {
    String myFilePath;
    String myFileName;

    public FileStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected FileStorageData(FileStorageData storageData) {
      super(storageData);
      myFileName = storageData.myFileName;
      myFilePath = storageData.myFilePath;
    }

    public StorageData clone() {
      return new FileStorageData(this);
    }

    public String toString() {
      return "FileStorageData[" + myFileName + "]";
    }
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return StorageUtil.getVirtualFile(myFile);
  }


  public IFile getFile() {
    return myFile;
  }

  @Nullable
  protected Document loadDocument() throws StateStorage.StateStorageException {
    try {
      if (!myFile.exists() || myFile.length() == 0) {
        return null;
      }
      else {
        return loadDocumentImpl();
      }
    }
    catch (final JDOMException e) {
      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        "Cannot load settings from file '" + myFile.getPath() + "': " + e.getLocalizedMessage() + "\n" +
                                        "File content will be recreated",
                                        "Load Settings",
                                        JOptionPane.ERROR_MESSAGE);
        }
      });

      /*
      throw new StateStorage.StateStorageException(
        "Error while parsing " + myFile.getAbsolutePath() + ".\nFile is probably corrupted:\n" + e.getMessage(), e);
        */
      return createEmptyElement();
    }
    catch (final IOException e) {
      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        "Cannot load settings from file '" + myFile.getPath() + "': " + e.getLocalizedMessage() + "\n" +
                                        "File content will be recreated",
                                        "Load Settings",
                                        JOptionPane.ERROR_MESSAGE);
        }
      });

      return createEmptyElement();

      //throw new StateStorage.StateStorageException("Error while loading " + myFile.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  private Document loadDocumentImpl() throws IOException, JDOMException {
    int bytesToSkip = skipBom();

    InputStream stream = new BufferedInputStream(myFile.openInputStream());
    try {
      if (bytesToSkip > 0) {
        synchronized (BUFFER) {
          int read = 0;
          while (read < bytesToSkip) {
            int r = stream.read(BUFFER, 0, bytesToSkip - read);
            if (r < 0) throw new IOException("Can't skip BOM for file: " + myFile.getPath());
            read += r;
          }
        }
      }

      return JDOMUtil.loadDocument(stream);
    }
    finally {
      stream.close();
    }
  }

  private int skipBom() {
    synchronized (BUFFER) {
      final int read;
      try {
        InputStream input = myFile.openInputStream();
        try {
          read = input.read(BUFFER);
        }
        finally {
          input.close();
        }

        if (Patches.SUN_BUG_ID_4508058) {
          if (startsWith(BUFFER, read, CharsetToolkit.UTF8_BOM)) {
            return CharsetToolkit.UTF8_BOM.length;
          }
        }
        return 0;
      }
      catch (IOException e) {
        return 0;
      }
    }
  }

  private static boolean startsWith(final byte[] buffer, final int read, final byte[] bom) {
    return read >= bom.length && ArrayUtil.startsWith(buffer, bom);
  }

  private Document createEmptyElement() {
    return new Document(new Element(myRootElementName));
  }

  public String getFileName() {
    return myFile.getName();
  }

  public String getFilePath() {
    return myFilePath;
  }

  public void setDefaultState(final Element element) {
    element.setName(myRootElementName);
    super.setDefaultState(element);
  }

}
