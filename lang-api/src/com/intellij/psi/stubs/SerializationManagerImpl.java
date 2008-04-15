/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SerializationManagerImpl extends SerializationManager implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManagerImpl");

  private PersistentStringEnumerator myNameStorage;

  private final Map<Integer, StubSerializer<? extends StubElement>> myIdToSerializer = new HashMap<Integer, StubSerializer<? extends StubElement>>();
  private final Map<StubSerializer<? extends StubElement>, Integer> mySerializerToId = new HashMap<StubSerializer<? extends StubElement>, Integer>();
  private final List<StubSerializer<? extends StubElement>> myAllSerializers = new ArrayList<StubSerializer<? extends StubElement>>();
  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean(false);
  private final File myFile = new File(PathManager.getSystemPath() + "/index/rep.names");

  public SerializationManagerImpl() {
    try {
      myNameStorage = new PersistentStringEnumerator(myFile);
    }
    catch (IOException e) {
      myNameStorageCrashed.set(true);
      LOG.info(e);
      repairNameStorage(); // need this in order for myNameStorage not to be null
      myNameStorageCrashed.set(true);
    }
    registerSerializer(PsiFileStubImpl.TYPE);
  }

  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  public void repairNameStorage() {
    if (myNameStorageCrashed.getAndSet(false)) {
      try {
        if (myNameStorage != null) {
          myNameStorage.close();
        }

        final File[] files = myFile.getParentFile().listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.getName().startsWith(myFile.getName())) {
              FileUtil.delete(file);
            }
          }
        }
        myNameStorage = new PersistentStringEnumerator(myFile);
        
        mySerializerToId.clear();
        myIdToSerializer.clear();
        for (StubSerializer<? extends StubElement> serializer : myAllSerializers) {
          assignId(serializer);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        myNameStorageCrashed.set(true);
      }
    }
  }

  public void registerSerializer(StubSerializer<? extends StubElement> serializer) {
    myAllSerializers.add(serializer);
    try {
      assignId(serializer);
    }
    catch (IOException e) {
      LOG.info(e);
      myNameStorageCrashed.set(true);
    }
  }

  private void assignId(final StubSerializer<? extends StubElement> serializer) throws IOException {
    final int id = persistentId(serializer);
    final StubSerializer old = myIdToSerializer.put(id, serializer);
    assert old == null : "ID: " + serializer.getExternalId() + " is not unique";

    final Integer oldId = mySerializerToId.put(serializer, id);
    assert oldId == null : "Serializer " + serializer + " is already registered";
  }

  private int persistentId(final StubSerializer<? extends StubElement> serializer) throws IOException {
    return myNameStorage.enumerate(serializer.getExternalId());
  }

  public void serialize(StubElement rootStub, DataOutputStream stream) {
    try {
      final StubSerializer serializer = getSerializer(rootStub);

      DataInputOutputUtil.writeINT(stream, getClassId(serializer));
      serializer.serialize(rootStub, stream, myNameStorage);

      final List<StubElement> children = rootStub.getChildrenStubs();
      DataInputOutputUtil.writeINT(stream, children.size());
      for (StubElement child : children) {
        serialize(child, stream);
      }
    }
    catch (IOException e) {
      LOG.info(e);
      myNameStorageCrashed.set(true);
    }
  }

  public StubSerializer getSerializer(final StubElement rootStub) {
    if (rootStub instanceof PsiFileStub) {
      final PsiFileStub fileStub = (PsiFileStub)rootStub;
      return fileStub.getType();
    }

    return rootStub.getStubType();
  }

  public StubElement deserialize(DataInputStream stream) {
    try {
      return deserialize(stream, null);
    }
    catch (IOException e) {
      myNameStorageCrashed.set(true);
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  private StubElement deserialize(DataInputStream stream, StubElement parentStub) throws IOException {
    final int id = DataInputOutputUtil.readINT(stream);
    final StubSerializer serializer = getClassById(id);
    
    assert serializer != null : "No serializer registered for stub: ID=" + id + "; parent stub class=" + parentStub.getClass().getName();
    
    StubElement stub = serializer.deserialize(stream, parentStub, myNameStorage);
    int childCount = DataInputOutputUtil.readINT(stream);
    for (int i = 0; i < childCount; i++) {
      deserialize(stream, stub);
    }
    return stub;
  }

  private int getClassId(final StubSerializer serializer) {
    return mySerializerToId.get(serializer).intValue();
  }

  private StubSerializer getClassById(int id) {
    return myIdToSerializer.get(id);
  }

  @NotNull
  public String getComponentName() {
    return "PSI.SerializationManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    try {
      myNameStorage.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}

