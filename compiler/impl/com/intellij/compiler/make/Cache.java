package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 8, 2003
 * Time: 7:03:56 PM
 */
public class Cache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.Cache");
  public static final int UNKNOWN = -1;

  private final PersistentHashMap<StorageClassId, ClassInfo> myQNameToClassInfoMap;

  private final BackwardDependenciesStorage myDependencies;
  private final CompilerDependencyStorage<StorageClassId> myQNameToReferencedClassesMap;
  private final CompilerDependencyStorage<StorageClassId> myQNameToSubclassesMap;
  private final PersistentHashMap<StorageClassId, Boolean> myRemoteQNames;
  private final String myStorePath;

  public Cache(@NonNls final String storePath, final int cacheSize) throws IOException {
    myStorePath = storePath;
    new File(storePath).mkdirs();
    myQNameToClassInfoMap = new CachedPersistentHashMap<StorageClassId, ClassInfo>(getOrCreateFile("classes"), ClassIdKeyDescriptor.INSTANCE, new DataExternalizer<ClassInfo>() {
      public void save(DataOutput out, ClassInfo value) throws IOException {
        value.save(out);
      }
      public ClassInfo read(DataInput in) throws IOException {
        return new ClassInfo(in);
      }
    }, cacheSize * 2) {
      protected boolean isValueDirty(ClassInfo classInfo) {
        return classInfo.isDirty();
      }
    };

    myDependencies = new BackwardDependenciesStorage(getOrCreateFile("bdeps"), cacheSize);
    myQNameToReferencedClassesMap = new CompilerDependencyStorage<StorageClassId>(getOrCreateFile("fdeps"), ClassIdKeyDescriptor.INSTANCE, cacheSize);
    myQNameToSubclassesMap = new CompilerDependencyStorage<StorageClassId>(getOrCreateFile("subclasses"), ClassIdKeyDescriptor.INSTANCE, cacheSize);

    myRemoteQNames = new PersistentHashMap<StorageClassId, Boolean>(getOrCreateFile("remote"), ClassIdKeyDescriptor.INSTANCE, new DataExternalizer<Boolean>() {
      public void save(DataOutput out, Boolean value) throws IOException {
        out.writeBoolean(value.booleanValue());
      }

      public Boolean read(DataInput in) throws IOException {
        return in.readBoolean();
      }
    }, cacheSize);
  }

  private File getOrCreateFile(final String fileName) throws IOException {
    final File file = new File(myStorePath, fileName);
    FileUtil.createIfDoesntExist(file);
    return file;
  }

  public void dispose() throws CacheCorruptedException {
    CacheCorruptedException ex = null;
    try {
      myQNameToClassInfoMap.close();
    }
    catch (IOException e) {
      LOG.info(e);
      ex = new CacheCorruptedException(e);
    }
    try {
      myRemoteQNames.close();
    }
    catch (IOException e) {
      LOG.info(e);
      if (ex != null) {
        ex = new CacheCorruptedException(e);
      }
    }

    myQNameToReferencedClassesMap.dispose();
    myDependencies.dispose();
    myQNameToSubclassesMap.dispose();

    if (ex != null) {
      throw ex;
    }

  }

  public int[] getAllClassNames() throws CacheCorruptedException {
    try {
      final Collection<StorageClassId> allKeys = myQNameToClassInfoMap.getAllKeysWithExistingMapping();
      final int[] array = ArrayUtil.newIntArray(allKeys.size());
      int idx = 0;
      for (StorageClassId id : allKeys) {
        array[idx++] = id.getClassQName();
      }
      return array;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int importClassInfo(ClassFileReader reader, SymbolTable symbolTable) throws ClsFormatException, CacheCorruptedException {
    try {
      final ClassInfo classInfo = new ClassInfo(reader, symbolTable);
      myQNameToClassInfoMap.put(new StorageClassId(classInfo.getQualifiedName()), classInfo);
      return classInfo.getQualifiedName();
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void importClassInfo(Cache fromCache, final int qName) throws CacheCorruptedException {
    try {
      final StorageClassId storageClassId = new StorageClassId(qName);
      final ClassInfo classInfo = fromCache.myQNameToClassInfoMap.get(storageClassId);
      if (classInfo != null) {
        final ClassInfo clone = classInfo.clone();
        clone.clearReferences();
        myQNameToClassInfoMap.put(storageClassId, clone);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getRuntimeVisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getRuntimeVisibleAnnotations() : AnnotationConstantValue.EMPTY_ARRAY;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getRuntimeInvisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getRuntimeInvisibleAnnotations() : AnnotationConstantValue.EMPTY_ARRAY;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getSubclasses(int classId) throws CacheCorruptedException {
    try {
      return myQNameToSubclassesMap.getValues(new StorageClassId(classId));
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      myQNameToSubclassesMap.addValue(new StorageClassId(classId), subclassQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      myQNameToSubclassesMap.removeValue(new StorageClassId(classId), subclassQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getReferencedClasses(int classId) throws CacheCorruptedException {
    try {
      return myQNameToReferencedClassesMap.getValues(new StorageClassId(classId));
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void clearReferencedClasses(int qName) throws CacheCorruptedException {
    try {
      myQNameToReferencedClassesMap.remove(new StorageClassId(qName));
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public Collection<ReferenceInfo> getReferences(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? Arrays.asList(classInfo.getReferences()) : Collections.<ReferenceInfo>emptyList();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public String getSourceFileName(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getSourceFileName() : "";
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public boolean isRemote(int classId) throws CacheCorruptedException {
    try {
      return myRemoteQNames.containsMapping(new StorageClassId(classId));
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void setRemote(int classId, boolean remote) throws CacheCorruptedException {
    try {
      final StorageClassId key = new StorageClassId(classId);
      if (remote) {
        myRemoteQNames.put(key, Boolean.TRUE);
      }
      else {
        myRemoteQNames.remove(key);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getSuperQualifiedName(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getSuperQualifiedName() : UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public String getPath(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getPath() : "";
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void setPath(int classId, String path) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      if (classInfo != null) {
        classInfo.setPath(path);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }
  
  public int getGenericSignature(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getGenericSignature() : UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public boolean containsClass(int qName) throws CacheCorruptedException {
    try {
      return myQNameToClassInfoMap.containsMapping(new StorageClassId(qName));
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getSuperInterfaces(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getSuperInterfaces() : ArrayUtil.EMPTY_INT_ARRAY;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFlags(int classId) throws CacheCorruptedException {
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classId));
      return classInfo != null? classInfo.getFlags() : UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public FieldInfo[] getFields(int qName) throws CacheCorruptedException{
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(qName));
      return classInfo != null? classInfo.getFields() : FieldInfo.EMPTY_ARRAY;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  @Nullable
  public FieldInfo findField(final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException{
    try {
      for (FieldInfo fieldInfo : getFields(classDeclarationId)) {
        if (fieldInfo.getName() == name && fieldInfo.getDescriptor() == descriptor) {
          return fieldInfo;
        }
      }
      return null;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  @Nullable
  public FieldInfo findFieldByName(final int classDeclarationId, final int name) throws CacheCorruptedException{
    try {
      for (FieldInfo fieldInfo : getFields(classDeclarationId)) {
        if (fieldInfo.getName() == name) {
          return fieldInfo;
        }
      }
      return null;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public MethodInfo[] getMethods(int classQName) throws CacheCorruptedException{
    try {
      final ClassInfo classInfo = myQNameToClassInfoMap.get(new StorageClassId(classQName));
      return classInfo != null? classInfo.getMethods() : MethodInfo.EMPTY_ARRAY;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  @Nullable
  public MethodInfo findMethod(final int classQName, final int name, final int descriptor) throws CacheCorruptedException{
    try {
      for (MethodInfo methodInfo : getMethods(classQName)) {
        if (methodInfo.getName() == name && methodInfo.getDescriptor() == descriptor) {
          return methodInfo;
        }
      }
      return null;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public List<MethodInfo> findMethodsByName(final int classDeclarationId, final int name) throws CacheCorruptedException{
    try {
      final List<MethodInfo> methods = new ArrayList<MethodInfo>();
      for (MethodInfo methodInfo : getMethods(classDeclarationId)) {
        if (methodInfo.getName() == name) {
          methods.add(methodInfo);
        }
      }
      return methods;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  @Nullable
  public MethodInfo findMethodsBySignature(final int classDeclarationId, final String signature, SymbolTable symbolTable) throws CacheCorruptedException{
    try {
      for (MethodInfo methodInfo : getMethods(classDeclarationId)) {
        if (signature.equals(CacheUtils.getMethodSignature(symbolTable.getSymbol(methodInfo.getName()), symbolTable.getSymbol(methodInfo.getDescriptor())))) {
          return methodInfo;
        }
      }
      return null;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addClassReferencer(int qName, int referencerQName) throws CacheCorruptedException {
    try {
      if (qName == referencerQName) {
        return; // do not log self-dependencies
      }
      if (myQNameToClassInfoMap.containsMapping(new StorageClassId(qName))) {
        myDependencies.addClassReferencer(qName, referencerQName);
        myQNameToReferencedClassesMap.addValue(new StorageClassId(referencerQName), qName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeClassReferencer(int qName, int referencerQName) throws CacheCorruptedException {
    try {
      myDependencies.removeReferencer(qName, referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addFieldReferencer(int qName, int fieldName, int referencerQName) throws CacheCorruptedException {
    try {
      if (qName != referencerQName && myQNameToClassInfoMap.containsMapping(new StorageClassId(qName))) {
        myDependencies.addFieldReferencer(qName, referencerQName, fieldName);
        myQNameToReferencedClassesMap.addValue(new StorageClassId(referencerQName), qName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addMethodReferencer(int qName, int methodName, int methodDescriptor, int referencerQName) throws CacheCorruptedException {
    try {
      if (qName != referencerQName && myQNameToClassInfoMap.containsMapping(new StorageClassId(qName))) {
        myDependencies.addMethodReferencer(qName, referencerQName, methodName, methodDescriptor);
        myQNameToReferencedClassesMap.addValue(new StorageClassId(referencerQName), qName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  /** @NotNull */
  public Dependency[] getBackDependencies(final int classQName) throws CacheCorruptedException{
    return myDependencies.getDependencies(classQName);
  }

  public void wipe() {
    myDependencies.dispose();
    myQNameToReferencedClassesMap.dispose();
    myQNameToSubclassesMap.dispose();
    try {
      myQNameToClassInfoMap.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    try {
      myRemoteQNames.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    final File[] files = new File(myStorePath).listFiles();
    if (files != null) {
      for (File file : files) {
        if (!file.isDirectory()) {
          FileUtil.delete(file);
        }
      }
    }
  }

  public void removeClass(final int qName) throws CacheCorruptedException {
    try {
      final StorageClassId classId = new StorageClassId(qName);
      final ClassInfo classInfo = myQNameToClassInfoMap.get(classId);
      if (classInfo == null) {
        return;
      }
      myDependencies.remove(qName);
      myQNameToClassInfoMap.remove(classId);
      myQNameToReferencedClassesMap.remove(classId);
      myQNameToSubclassesMap.remove(classId);
      myRemoteQNames.remove(classId);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }
}
