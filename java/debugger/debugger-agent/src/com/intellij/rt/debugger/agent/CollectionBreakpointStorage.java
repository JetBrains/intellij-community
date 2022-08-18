// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.debugger.agent;


import org.jetbrains.capture.org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class CollectionBreakpointStorage {
  private static final ConcurrentMap<CapturedField, FieldHistory> FIELD_MODIFICATIONS_STORAGE;
  private static final ConcurrentMap<CollectionWrapper, CollectionHistory> COLLECTION_MODIFICATIONS_STORAGE;
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  private static boolean ENABLED; // set from debugger

  static {
    FIELD_MODIFICATIONS_STORAGE = new ConcurrentHashMap<CapturedField, FieldHistory>();
    COLLECTION_MODIFICATIONS_STORAGE = new ConcurrentHashMap<CollectionWrapper, CollectionHistory>();
  }

  public static void saveFieldModification(String internalClsName,
                                           String fieldName,
                                           Object clsInstance,
                                           Object collectionInstance,
                                           boolean shouldSaveStack) {
    if (!ENABLED) {
      return;
    }
    String clsName = Type.getObjectType(internalClsName).getClassName();
    CapturedField field = new CapturedField(clsName, fieldName, clsInstance);
    FIELD_MODIFICATIONS_STORAGE.putIfAbsent(field, new FieldHistory());
    FieldHistory history = FIELD_MODIFICATIONS_STORAGE.get(field);
    Throwable exception = shouldSaveStack ? new Throwable() : null;
    history.add(new FieldModificationInfo(exception, collectionInstance));
  }

  public static void saveCollectionModification(Object collectionInstance, Object elem, boolean isAddition) {
    if (!ENABLED) {
      return;
    }
    CollectionWrapper wrapper = new CollectionWrapper(collectionInstance);
    COLLECTION_MODIFICATIONS_STORAGE.putIfAbsent(wrapper, new CollectionHistory());
    CollectionHistory history = COLLECTION_MODIFICATIONS_STORAGE.get(wrapper);
    Throwable exception = new Throwable();
    history.add(new CollectionModificationInfo(exception, elem, isAddition));
  }

  @SuppressWarnings("unused")
  public static Object[] getCollectionModifications(Object collectionInstance) {
    CollectionWrapper wrapper = new CollectionWrapper(collectionInstance);
    CollectionHistory history = COLLECTION_MODIFICATIONS_STORAGE.get(wrapper);
    return history == null ? EMPTY_OBJECT_ARRAY : history.get();
  }

  @SuppressWarnings("unused")
  public static Object[] getFieldModifications(String clsName, String fieldName, Object clsInstance) {
    CapturedField field = new CapturedField(clsName, fieldName, clsInstance);
    FieldHistory history = FIELD_MODIFICATIONS_STORAGE.get(field);
    return history == null ? EMPTY_OBJECT_ARRAY : history.getCollectionInstances();
  }

  @SuppressWarnings("unused")
  public static String getStack(Object collectionInstance, int modificationIndex) throws IOException {
    CollectionWrapper wrapper = new CollectionWrapper(collectionInstance);
    CollectionHistory history = COLLECTION_MODIFICATIONS_STORAGE.get(wrapper);
    return history == null ? "" : wrapInString(history.get(modificationIndex));
  }

  @SuppressWarnings("unused")
  public static String getStack(String clsName, String fieldName, Object clsInstance, int modificationIndex) throws IOException {
    CapturedField field = new CapturedField(clsName, fieldName, clsInstance);
    FieldHistory history = FIELD_MODIFICATIONS_STORAGE.get(field);
    return history == null ? "" : wrapInString(history.get(modificationIndex));
  }

  private static String wrapInString(CapturedStackInfo info) throws IOException {
    if (info == null) {
      return "";
    }
    ByteArrayOutputStream bas = new ByteArrayOutputStream();
    DataOutputStream dos = null;
    try {
      dos = new DataOutputStream(bas);
      for (StackTraceElement stackTraceElement : info.getStackTrace()) {
        if (stackTraceElement != null) {
          dos.writeUTF(stackTraceElement.getClassName());
          dos.writeUTF(stackTraceElement.getMethodName());
          dos.writeInt(stackTraceElement.getLineNumber());
        }
      }
      return bas.toString("ISO-8859-1");
    }
    finally {
      if (dos != null) {
        dos.close();
      }
    }
  }

  private static class FieldHistory {
    private final ArrayList<FieldModificationInfo> myModifications = new ArrayList<FieldModificationInfo>();
    private final ReentrantLock myLock = new ReentrantLock();

    private void add(FieldModificationInfo info) {
      myLock.lock();
      try {
        myModifications.add(info);
      }
      finally {
        myLock.unlock();
      }
    }

    private FieldModificationInfo get(int modificationIndex) {
      myLock.lock();
      try {
        return myModifications.get(modificationIndex);
      }
      finally {
        myLock.unlock();
      }
    }

    private Object[] getCollectionInstances() {
      myLock.lock();
      try {
        ArrayList<Object> collectionInstances = new ArrayList<Object>();
        for (FieldModificationInfo info : myModifications) {
          collectionInstances.add(info.myCollectionInstance);
        }
        return collectionInstances.toArray();
      }
      finally {
        myLock.unlock();
      }
    }
  }

  private static class CollectionHistory {
    private final ArrayList<CollectionModificationInfo> myOperations = new ArrayList<CollectionModificationInfo>();
    private final ReentrantLock myLock = new ReentrantLock();

    private void add(CollectionModificationInfo info) {
      myLock.lock();
      try {
        myOperations.add(info);
      }
      finally {
        myLock.unlock();
      }
    }

    private Object[] get() {
      myLock.lock();
      try {
        return myOperations.toArray();
      }
      finally {
        myLock.unlock();
      }
    }

    private CollectionModificationInfo get(int operationIndex) {
      myLock.lock();
      try {
        return myOperations.get(operationIndex);
      }
      finally {
        myLock.unlock();
      }
    }
  }

  private static class CapturedStackInfo {
    private final Throwable myException;

    private CapturedStackInfo(Throwable exception) {
      myException = exception;
    }

    public List<StackTraceElement> getStackTrace() {
      StackTraceElement[] stackTrace = myException.getStackTrace();
      int startIndex = this instanceof CollectionModificationInfo ? 3 : 2;
      if (startIndex > stackTrace.length - 1) {
        return Collections.emptyList();
      }
      return Arrays.asList(stackTrace).subList(startIndex, stackTrace.length);
    }
  }

  private static class CollectionModificationInfo extends CapturedStackInfo {
    private final Object myElement;
    private final boolean myIsAddition;

    private CollectionModificationInfo(Throwable exception, Object elem, boolean isAddition) {
      super(exception);
      myElement = elem;
      myIsAddition = isAddition;
    }

    @SuppressWarnings("unused")
    private Object getElement() {
      return myElement;
    }

    @SuppressWarnings("unused")
    private boolean isAddition() {
      return myIsAddition;
    }
  }

  private static class FieldModificationInfo extends CapturedStackInfo {
    private final Object myCollectionInstance;

    private FieldModificationInfo(Throwable exception, Object collectionInstance) {
      super(exception);
      myCollectionInstance = collectionInstance;
    }
  }

  private static class CollectionWrapper {
    private final Object myCollectionInstance;

    private CollectionWrapper(Object collectionInstance) {
      myCollectionInstance = collectionInstance;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CollectionWrapper &&
             ((CollectionWrapper)obj).myCollectionInstance == myCollectionInstance;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(myCollectionInstance);
    }
  }

  private static class CapturedField {
    final String myClsName;
    final String myFieldName;
    final Object myClsInstance;

    private CapturedField(String clsName, String fieldName, Object clsInstance) {
      myClsName = clsName;
      myFieldName = fieldName;
      myClsInstance = clsInstance;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof CapturedField &&
             myClsInstance == ((CapturedField)obj).myClsInstance &&
             myFieldName.equals(((CapturedField)obj).myFieldName) &&
             myClsName.equals(((CapturedField)obj).myClsName);
    }

    @Override
    public int hashCode() {
      return 31 * myFieldName.hashCode() +
             13 * myClsName.hashCode() +
             System.identityHashCode(myClsInstance);
    }
  }
}
