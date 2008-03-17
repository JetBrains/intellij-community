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

  private final Map<Class, StubSerializer> myStubClassToSerializer = new HashMap<Class, StubSerializer>();
  private final Map<Integer, StubSerializer> myIdToSerializer = new HashMap<Integer, StubSerializer>();
  private final Map<StubSerializer, Integer> mySerializerToId = new HashMap<StubSerializer, Integer>();

  public SerializationManagerImpl() {
    try {
      myNameStorage = new PersistentStringEnumerator(new File(PathManager.getSystemPath() + "/rep.names"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    registerSerializer(PsiFileStub.class, new StubSerializer<PsiFileStub>() {
      public String getExternalId() {
        return "PsiFile.basic";
      }

      public void serialize(final PsiFileStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
          throws IOException {
      }

      public PsiFileStub deserialize(final DataInputStream dataStream,
                                     final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException {
        return new PsiFileStubImpl();
      }

      public void indexStub(final PsiFileStub stub, final IndexSink sink) {
      }
    });
  }

  public <T extends StubElement> void registerSerializer(Class<T> stubClass, StubSerializer<T> serializer) {
    try {
      myStubClassToSerializer.put(stubClass, serializer);
      final int id = myNameStorage.enumerate(serializer.getExternalId());
      myIdToSerializer.put(id, serializer);
      mySerializerToId.put(serializer, id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public StubSerializer getSerializer(Class<? extends StubElement> stubClass) {
    for (Map.Entry<Class, StubSerializer> entry : myStubClassToSerializer.entrySet()) {
      if (entry.getKey().isAssignableFrom(stubClass)) return entry.getValue();
    }

    throw new IllegalStateException("Can't find stub serializer for " + stubClass.getName());
  }

  public void serialize(StubElement rootStub, DataOutputStream stream) {
    try {
      final Class<? extends StubElement> stubClass = rootStub.getClass();
      final StubSerializer serializer = getSerializer(stubClass);

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

  public StubElement deserialize(DataInputStream stream) {
    try {
      return deserialize(stream, null);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private StubElement deserialize(DataInputStream stream, StubElement parentStub) throws IOException {
    final StubSerializer serializer = getClassById(DataInputOutputUtil.readINT(stream));
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

