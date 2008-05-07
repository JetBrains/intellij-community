package com.intellij.compiler.impl.packagingCompiler;

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 23, 2003
 * Time: 2:58:25 PM
 */

import com.intellij.CommonBundle;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public abstract class MapCache <T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.MapCache");

  protected Map<String, T> myMap = null;
  protected final File myStoreFile;
  private boolean myIsDirty = false;

  MapCache(String storePath) {
    myStoreFile = new File(storePath);
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  protected void setDirty() {
    myIsDirty = true;
  }

  protected boolean load() {
    if (myMap == null) {
      myMap = new HashMap<String, T>();
      myIsDirty = false;
      if (myStoreFile.exists() && myStoreFile.length() > 0L) {
        try {
          byte[] bytes = FileUtil.loadFileBytes(myStoreFile);
          DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(bytes));
          try {
            int version = inputStream.readInt();
            if (version == CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION) {
              int size = inputStream.readInt();
              myMap = new HashMap<String,T>();

              for (int i = 0; i < size; i++) {
                String key = CompilerIOUtil.readString(inputStream);
                T value = read(inputStream);
                myMap.put(key, value);
              }
            }
          }
          finally {
            inputStream.close();
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }
    }
    return true;
  }

  protected abstract T read(DataInputStream stream) throws IOException;

  protected abstract void write(T t, DataOutputStream stream) throws IOException;

  public void save() {
    if (myMap == null) {
      return; // nothing to save
    }
    try {
      FileUtil.createParentDirs(myStoreFile);
      final DataOutputStream outStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myStoreFile)));

      try {
        outStream.writeInt(CompilerConfigurationImpl.DEPENDENCY_FORMAT_VERSION);
        final Iterator<String> keysIterator = myMap.keySet().iterator();
        outStream.writeInt(myMap.size());
        while (keysIterator.hasNext()) {
          final String key = keysIterator.next();
          final T value = myMap.get(key);
          CompilerIOUtil.writeString(key, outStream);
          write(value, outStream);
        }
        myIsDirty = false;
      }
      finally {
        outStream.close();
      }
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        final String message = CompilerBundle.message("error.saving.data.to.file", myStoreFile.getPath(), e.getMessage());
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showMessageDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon());
          }
        });
      }
    }
  }

  public boolean wipe() {
    myMap = null;
    myIsDirty = false;
    return myStoreFile.delete();
  }

}
