/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerializationManagerImpl extends SerializationManager implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManagerImpl");

  private final PersistentStringEnumerator myNameStorage;

  private final Map<Integer, StubSerializer> myIdToSerializer = new HashMap<Integer, StubSerializer>();
  private final Map<StubSerializer, Integer> mySerializerToId = new HashMap<StubSerializer, Integer>();

  public SerializationManagerImpl() {
    try {
      myNameStorage = new PersistentStringEnumerator(new File(PathManager.getSystemPath() + "/rep.names"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    registerSerializer(PsiFileStubImpl.TYPE);
  }

  public <T extends StubElement> void registerSerializer(StubSerializer<T> serializer) {
    try {
      final int id = persistentId(serializer);
      final StubSerializer old = myIdToSerializer.put(id, serializer);
      assert old == null : "ID: " + serializer.getExternalId() + " is not unique";

      final Integer oldId = mySerializerToId.put(serializer, id);
      assert oldId == null : "Serializer " + serializer + " is already registered";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private <T extends StubElement> int persistentId(final StubSerializer<T> serializer) throws IOException {
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
      throw new RuntimeException(e);
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
      throw new RuntimeException(e);
    }
  }

  private StubElement deserialize(DataInputStream stream, StubElement parentStub) throws IOException {
    final int id = DataInputOutputUtil.readINT(stream);
    final StubSerializer serializer = getClassById(id);
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

