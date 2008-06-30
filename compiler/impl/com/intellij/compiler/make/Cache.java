package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import gnu.trove.*;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 8, 2003
 * Time: 7:03:56 PM
 */
public class Cache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.Cache");

  private final ViewPool myViewPool;
  private volatile TIntIntHashMap myQNameToClassInfoIdMap;
  private volatile TIntIntHashMap myQNameToClassDeclarationIdMap;
  public static final int UNKNOWN = -1;

  private final File myDeclarationsIndexFile;
  private final File myClassInfosIndexFile;
  @NonNls private static final String DECLARATIONS_INDEX_FILE_NAME = "declarations_index.dat";
  @NonNls private static final String CLASSINFO_INDEX_FILE_NAME = "classinfo_index.dat";

  public Cache(@NonNls String storePath, final int initialCacheSize, final boolean canResize) {
    myViewPool = new ViewPool(storePath, initialCacheSize, canResize);
    myDeclarationsIndexFile = new File(storePath + "/" + DECLARATIONS_INDEX_FILE_NAME);
    myClassInfosIndexFile = new File(storePath + "/" + CLASSINFO_INDEX_FILE_NAME);
  }

  public void dispose() throws CacheCorruptedException {
    synchronized (myViewPool) {
      myViewPool.dispose(true);
    }
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        if (myQNameToClassDeclarationIdMap != null) {
          writeIndexMap(myQNameToClassDeclarationIdMap, myDeclarationsIndexFile);
          myQNameToClassDeclarationIdMap = null;
        }
      }
      synchronized (myViewPool.getClassInfosLock()) {
        if (myQNameToClassInfoIdMap != null) {
          writeIndexMap(myQNameToClassInfoIdMap, myClassInfosIndexFile);
          myQNameToClassInfoIdMap = null;
        }
      }
    }
    catch (IOException e) {
      myDeclarationsIndexFile.delete();
      myClassInfosIndexFile.delete();
      LOG.info(e);
    }
  }

  public int[] getAllClassNames() throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        return getQNameToClassInfoIdMap().keys();
      }
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int importClassInfo(ClassFileReader reader, SymbolTable symbolTable) throws ClsFormatException, CacheCorruptedException {
    try {
      final int qName = symbolTable.getId(reader.getQualifiedName());
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView classInfoView = myViewPool.getClassInfoView(getClassId(qName));
        final int id = classInfoView.getRecordId();

        classInfoView.setQualifiedName(qName);

        final String signature = reader.getGenericSignature();
        final int genericSignature = signature != null? symbolTable.getId(signature) : -1;
        classInfoView.setGenericSignature(genericSignature);

        classInfoView.setPath(reader.getPath());

        final String superClass = reader.getSuperClass();
        final int superQName = "".equals(superClass)? UNKNOWN : symbolTable.getId(superClass);

        LOG.assertTrue(superQName != qName);

        classInfoView.setSuperQualifiedName(superQName);

        final String[] superInterfaces = reader.getSuperInterfaces();
        final int[] interfaceNames = new int[superInterfaces.length];
        for (int idx = 0; idx < superInterfaces.length; idx++) {
          interfaceNames[idx] = symbolTable.getId(superInterfaces[idx]);
        }
        classInfoView.setSuperInterfaces(interfaceNames);

        final String sourceFileName = reader.getSourceFileName();
        if (sourceFileName != null) {
          classInfoView.setSourceFileName(sourceFileName);
        }

        classInfoView.setFlags(reader.getAccessFlags());

        classInfoView.setRuntimeVisibleAnnotations(reader.getRuntimeVisibleAnnotations());

        classInfoView.setRuntimeInvisibleAnnotations(reader.getRuntimeInvisibleAnnotations());

        classInfoView.setReferences(reader.getReferences());

        getQNameToClassInfoIdMap().put(qName, id);
      }

      final FieldInfo[] fields = reader.getFields();
      final MethodInfo[] methods = reader.getMethods();
      MemberInfo[] members = ArrayUtil.mergeArrays(fields, methods, MemberInfo.class);
      updateMemberDeclarations(qName, members);
      return qName;
    }
    catch (ClsFormatException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void importClassInfo(Cache fromCache, final int qName) throws CacheCorruptedException {
    try {
      final int fromClassId = fromCache.getClassId(qName);

      LOG.assertTrue(fromClassId != UNKNOWN);

      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView classInfoView = myViewPool.getClassInfoView(getClassId(qName));
        classInfoView.setQualifiedName(qName);
        classInfoView.setGenericSignature(fromCache.getGenericSignature(fromClassId));
        classInfoView.setPath(fromCache.getPath(fromClassId));

        final int superQualifiedName = fromCache.getSuperQualifiedName(fromClassId);
        LOG.assertTrue(qName != superQualifiedName);
        classInfoView.setSuperQualifiedName(superQualifiedName);

        classInfoView.setSuperInterfaces(fromCache.getSuperInterfaces(fromClassId));

        classInfoView.setSourceFileName(fromCache.getSourceFileName(fromClassId));

        classInfoView.setFlags(fromCache.getFlags(fromClassId));

        classInfoView.setRuntimeVisibleAnnotations(fromCache.getRuntimeVisibleAnnotations(fromClassId));

        classInfoView.setRuntimeInvisibleAnnotations(fromCache.getRuntimeInvisibleAnnotations(fromClassId));

        getQNameToClassInfoIdMap().put(qName, classInfoView.getRecordId());
      }

      final int fromClassDeclarationId = fromCache.getClassDeclarationId(qName);
      final int[] fromFieldIds = fromCache.getFieldIds(fromClassDeclarationId);
      final int[] fromMethodIds = fromCache.getMethodIds(fromClassDeclarationId);
      final MemberInfo[] members = new MemberInfo[fromFieldIds.length + fromMethodIds.length];
      int currentMemberIndex = 0;
      for (int fromFieldId : fromFieldIds) {
        members[currentMemberIndex++] = fromCache.createFieldInfo(fromFieldId);
      }
      for (final int methodId : fromMethodIds) {
        members[currentMemberIndex++] = fromCache.createMethodInfo(methodId);
      }
      updateMemberDeclarations(qName, members);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getRuntimeVisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getRuntimeVisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getRuntimeInvisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getRuntimeInvisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getClassId(int qName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final TIntIntHashMap classInfoMap = getQNameToClassInfoIdMap();
        if (classInfoMap.containsKey(qName)) {
          return classInfoMap.get(qName);
        }
      }
      return UNKNOWN;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getClassDeclarationId(final int qName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final TIntIntHashMap declarationsMap = getQNameToClassDeclarationIdMap();
        if (declarationsMap.containsKey(qName)) {
          return declarationsMap.get(qName);
        }
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(UNKNOWN);
        final int id = view.getRecordId();
        declarationsMap.put(qName, id);
        return id;
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getSubclasses(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getSubclasses();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView reader = myViewPool.getClassInfoView(classId);
        reader.addSubclass(subclassQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        view.removeSubclass(subclassQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getReferencedClassQNames(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getReferencedClasses();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public Collection<ReferenceInfo> getReferences(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getReferences();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public String getSourceFileName(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getSourceFileName();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void setSourceFileName(int classId, String name) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        view.setSourceFileName(name);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public boolean isRemote(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.isRemote();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void setRemote(int classId, boolean remote) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        view.setRemote(remote);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getSuperQualifiedName(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getSuperQualifiedName();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public String getPath(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getPath();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void setPath(int classId, String path) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        view.setPath(path);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getGenericSignature(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getGenericSignature();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getSuperInterfaces(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getSuperInterfaces();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFlags(int classId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        return view.getFlags();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addReferencedClass(int classId, int referencedClassName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassInfosLock()) {
        final ClassInfoView view = myViewPool.getClassInfoView(classId);
        view.addReferencedClass(referencedClassName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getFieldIds(int classDeclarationId) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        return view.getFieldIds();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int findField(final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        final int[] ids = view.getFieldIds();
        for (final int id : ids) {
          final NameDescriptorPair pair = view.getFieldNameAndDescriptor(id);
          if (pair != null && pair.name == name && pair.descriptor == descriptor) {
            return id;
          }
        }
      }
      return UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int findFieldByName(final int classDeclarationId, final int name) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        final int[] ids = view.getFieldIds();
        for (final int id : ids) {
          final NameDescriptorPair pair = view.getFieldNameAndDescriptor(id);
          if (pair != null && pair.name == name) {
            return id;
          }
        }
      }
      return UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int findMethod(final int classDeclarationId, final int name, final int descriptor) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        final int[] ids = view.getMethodIds();
        for (final int id : ids) {
          final NameDescriptorPair pair = view.getMethodNameAndDescriptor(id);
          if (pair != null && pair.name == name && pair.descriptor == descriptor) {
            return id;
          }
        }
      }
      return UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] findMethodsByName(final int classDeclarationId, final int name) throws CacheCorruptedException{
    try {
      final TIntArrayList list;
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        final int[] ids = view.getMethodIds();
        list = new TIntArrayList();
        for (final int id : ids) {
          final NameDescriptorPair pair = view.getMethodNameAndDescriptor(id);
          if (pair != null && pair.name == name) {
            list.add(id);
          }
        }
      }
      return list.toNativeArray();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int findMethodsBySignature(final int classDeclarationId, final String signature, SymbolTable symbolTable) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        final int[] ids = view.getMethodIds();
        for (int methodId : ids) {
          final NameDescriptorPair pair = view.getMethodNameAndDescriptor(methodId);
          if (pair != null && signature.equals(CacheUtils.getMethodSignature(symbolTable.getSymbol(pair.name), symbolTable.getSymbol(pair.descriptor)))) {
            return methodId;
          }
        }
      }
      return UNKNOWN;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getMethodIds(int classDeclarationId) throws CacheCorruptedException{
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        return view.getMethodIds();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addClassReferencer(int classDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        view.addReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeClassReferencer(int classDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        view.removeReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFieldName(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getName();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFieldDescriptor(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getDescriptor();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFieldGenericSignature(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getGenericSignature();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getFieldFlags(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getFlags();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public ConstantValue getFieldConstantValue(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getConstantValue();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getFieldRuntimeVisibleAnnotations(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getRuntimeVisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getFieldRuntimeInvisibleAnnotations(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getRuntimeInvisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addFieldReferencer(int fieldId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
        view.addReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeFieldReferencer(int fieldId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
        view.removeReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getMethodName(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getName();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getMethodDescriptor(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getDescriptor();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getMethodGenericSignature(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getGenericSignature();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int getMethodFlags(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getFlags();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getMethodRuntimeVisibleAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getRuntimeVisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[] getMethodRuntimeInvisibleAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getRuntimeInvisibleAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[][] getMethodRuntimeVisibleParamAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getRuntimeVisibleParamAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public AnnotationConstantValue[][] getMethodRuntimeInvisibleParamAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getRuntimeInvisibleParamAnnotations();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public ConstantValue getAnnotationDefault(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getAnnotationDefault();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public boolean isConstructor(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.isConstructor();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getMethodThrownExceptions(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getThrownExceptions();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addMethodReferencer(int methodDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        view.addReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void removeMethodReferencer(int methodDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        view.removeReferencer(referencerQName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  /** @NotNull */
  public Dependency[] getBackDependencies(final int classQName) throws CacheCorruptedException{
    final int classDeclarationId = getClassDeclarationId(classQName);
    if (classDeclarationId == UNKNOWN) {
      return Dependency.EMPTY_ARRAY;
    }
    try {
      final TIntObjectHashMap<Dependency> dependencies = new TIntObjectHashMap<Dependency>();
      final int[] classReferencers = getClassReferencers(classDeclarationId);
      for (final int referencer : classReferencers) {
        if (referencer != classQName) { // skip self-dependencies
          addDependency(dependencies, referencer);
        }
      }

      final int[] fieldIds = getFieldIds(classDeclarationId);
      for (final int fieldId : fieldIds) {
        final int[] fieldReferencers = getFieldReferencers(fieldId);
        for (int referencer : fieldReferencers) {
          if (referencer != classQName) { // skip self-dependencies
            final Dependency dependency = addDependency(dependencies, referencer);
            dependency.addMemberInfo(createFieldInfo(fieldId));
          }
        }
      }

      final int[] methodIds = getMethodIds(classDeclarationId);
      for (final int methodId : methodIds) {
        final int[] methodReferencers = getMethodReferencers(methodId);
        for (int referencer : methodReferencers) {
          if (referencer != classQName) {
            final Dependency dependency = addDependency(dependencies, referencer);
            dependency.addMemberInfo(createMethodInfo(methodId));
          }
        }
      }

      final Dependency[] dependencyArray = new Dependency[dependencies.size()];
      dependencies.forEachValue(new TObjectProcedure<Dependency>() {
        private int index = 0;
        public boolean execute(Dependency object) {
          dependencyArray[index++] = object;
          return true;
        }
      });
      return dependencyArray;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public FieldInfo createFieldInfo(final int fieldId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
        return new FieldInfo(
          view.getName(),
          view.getDescriptor(),
          view.getGenericSignature(),
          view.getFlags(),
          view.getConstantValue(),
          view.getRuntimeVisibleAnnotations(),
          view.getRuntimeInvisibleAnnotations()
        );
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public MethodInfo createMethodInfo(final int methodId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodId);
        return new MethodInfo(
          view.getName(),
          view.getDescriptor(),
          view.getGenericSignature(),
          view.getFlags(),
          view.getThrownExceptions(),
          view.isConstructor(),
          view.getRuntimeVisibleAnnotations(),
          view.getRuntimeInvisibleAnnotations(),
          view.getRuntimeVisibleParamAnnotations(),
          view.getRuntimeInvisibleParamAnnotations(),
          view.getAnnotationDefault()
        );
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  private static Dependency addDependency(TIntObjectHashMap<Dependency> container, int classQName) {
    Dependency dependency = container.get(classQName);
    if (dependency == null) {
      dependency = new Dependency(classQName);
      container.put(classQName, dependency);
    }
    return dependency;
  }

  public int[] getFieldReferencers(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getFieldDeclarationsLock()) {
        final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
        return view.getReferencers();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getMethodReferencers(int methodDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getMethodDeclarationsLock()) {
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
        return view.getReferencers();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public int[] getClassReferencers(int classDeclarationId) throws CacheCorruptedException {
    try {
      synchronized (myViewPool.getClassDeclarationsLock()) {
        final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
        return view.getReferencers();
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  private void updateMemberDeclarations(int classQName, MemberInfo[] classMembers) throws CacheCorruptedException{
    try {
      final int classDeclarationId = getClassDeclarationId(classQName);

      final int[] fieldIds = getFieldIds(classDeclarationId);
      final int[] methodIds = getMethodIds(classDeclarationId);
      final TObjectIntHashMap<MemberInfo> currentMembers = new TObjectIntHashMap<MemberInfo>();
      for (final int fieldId : fieldIds) {
        currentMembers.put(createFieldInfo(fieldId), fieldId);
      }
      for (final int methodId : methodIds) {
        currentMembers.put(createMethodInfo(methodId), methodId);
      }

      final TIntHashSet fieldsToRemove = new TIntHashSet(fieldIds);
      final TIntHashSet methodsToRemove = new TIntHashSet(methodIds);

      for (final MemberInfo classMember : classMembers) {
        if (currentMembers.containsKey(classMember)) { // changed
          final int memberId = currentMembers.get(classMember);
          if (classMember instanceof FieldInfo) {
            fieldsToRemove.remove(memberId);
          }
          else if (classMember instanceof MethodInfo) {
            methodsToRemove.remove(memberId);
          }
          putMember(classDeclarationId, memberId, classMember);
        }
        else { // added
          putMember(classDeclarationId, UNKNOWN, classMember);
        }
      }

      if (!fieldsToRemove.isEmpty()) {
        final int[] fieldsArray = fieldsToRemove.toArray();
        for (int aFieldsArray : fieldsArray) {
          removeFieldDeclaration(classDeclarationId, aFieldsArray);
        }
      }

      if (!methodsToRemove.isEmpty()) {
        final int[] methodsArray = methodsToRemove.toArray();
        for (int aMethodsArray : methodsArray) {
          removeMethodDeclaration(classDeclarationId, aMethodsArray);
        }
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  private void removeMethodDeclaration(int classDeclarationId, int methodId) throws IOException, CacheCorruptedException {
    synchronized (myViewPool.getClassDeclarationsLock()) {
      final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
      classDeclarationView.removeMethodId(methodId);
    }
    synchronized (myViewPool.getMethodDeclarationsLock()) {
      myViewPool.removeMethodDeclarationRecord(myViewPool.getMethodDeclarationView(methodId));
    }
  }


  private void removeFieldDeclaration(int classDeclarationId, int fieldId) throws IOException, CacheCorruptedException {
    synchronized (myViewPool.getClassDeclarationsLock()) {
      final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
      classDeclarationView.removeFieldId(fieldId);
    }
    synchronized (myViewPool.getFieldDeclarationsLock()) {
      myViewPool.removeFieldDeclarationRecord(myViewPool.getFieldDeclarationView(fieldId));
    }
  }

  public int putMember(final int classDeclarationId, int memberId, final MemberInfo classMember) throws CacheCorruptedException {
    try {
      if (classMember instanceof FieldInfo) {
        final FieldInfo fieldInfo = (FieldInfo)classMember;
        if (memberId == UNKNOWN) {
          synchronized (myViewPool.getFieldDeclarationsLock()) {
            memberId = myViewPool.getFieldDeclarationView(memberId).getRecordId();
          }
          synchronized (myViewPool.getClassDeclarationsLock()) {
            myViewPool.getClassDeclarationView(classDeclarationId).addFieldId(memberId, fieldInfo.getName(), fieldInfo.getDescriptor());
          }
        }
        synchronized (myViewPool.getFieldDeclarationsLock()) {
          final FieldDeclarationView view = myViewPool.getFieldDeclarationView(memberId);
          view.setName(fieldInfo.getName());
          view.setDescriptor(fieldInfo.getDescriptor());
          view.setGenericSignature(fieldInfo.getGenericSignature());
          view.setFlags(fieldInfo.getFlags());
          view.setConstantValue(fieldInfo.getConstantValue());
          view.setRuntimeVisibleAnnotations(fieldInfo.getRuntimeVisibleAnnotations());
          view.setRuntimeInvisibleAnnotations(fieldInfo.getRuntimeInvisibleAnnotations());
        }
      }
      else if (classMember instanceof MethodInfo) {
        final MethodInfo methodInfo = (MethodInfo)classMember;
        if (memberId == UNKNOWN) {
          synchronized (myViewPool.getMethodDeclarationsLock()) {
            memberId = myViewPool.getMethodDeclarationView(memberId).getRecordId();
          }
          synchronized (myViewPool.getClassDeclarationsLock()) {
            myViewPool.getClassDeclarationView(classDeclarationId).addMethodId(memberId, methodInfo.getName(), methodInfo.getDescriptor());
          }
        }
        synchronized (myViewPool.getMethodDeclarationsLock()) {
          final MethodDeclarationView view = myViewPool.getMethodDeclarationView(memberId);
          view.setName(methodInfo.getName());
          view.setDescriptor(methodInfo.getDescriptor());
          view.setGenericSignature(methodInfo.getGenericSignature());
          view.setFlags(methodInfo.getFlags());
          view.setIsConstructor(methodInfo.isConstructor());
          view.setThrownExceptions(methodInfo.getThrownExceptions());
          view.setRuntimeVisibleAnnotations(methodInfo.getRuntimeVisibleAnnotations());
          view.setRuntimeInvisibleAnnotations(methodInfo.getRuntimeInvisibleAnnotations());
          view.setRuntimeVisibleParamAnnotations(methodInfo.getRuntimeVisibleParameterAnnotations());
          view.setRuntimeInvisibleParamAnnotations(methodInfo.getRuntimeInvisibleParameterAnnotations());
          view.setAnnotationDefault(methodInfo.getAnnotationDefault());
        }
      }
      else {
        LOG.assertTrue(false, "Unknown member info: "+ classMember.getClass().getName());
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
    return memberId;
  }

  private static void writeIndexMap(TIntIntHashMap map, File indexFile) throws IOException {
    indexFile.createNewFile();
    final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
    try {
      stream.writeInt(map.size());
      final IOException[] _ex = new IOException[] {null};
      map.forEachEntry(new TIntIntProcedure() {
        public boolean execute(int qName, int id) {
          try {
            stream.writeInt(qName);
            stream.writeInt(id);
          }
          catch (IOException e) {
            _ex[0]  = e;
            return false;
          }
          return true;
        }
      });
      if (_ex[0] != null) {
        throw _ex[0];
      }
    }
    finally {
      stream.close();
    }
  }

  private static void readIndexMap(TIntIntHashMap map, File indexFile) throws IOException {
    try {
      final byte[] bytes = FileUtil.loadFileBytes(indexFile);
      DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));
      try {
        int size = stream.readInt();
        while (size-- > 0) {
          final int qName = stream.readInt();
          final int id = stream.readInt();
          map.put(qName, id);
        }
      }
      finally {
        stream.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
  }

  public void wipe() throws CacheCorruptedException {
    synchronized (myViewPool) {
      myViewPool.wipe();
    }
    synchronized (myViewPool.getClassDeclarationsLock()) {
      myQNameToClassDeclarationIdMap = null;
    }
    synchronized (myViewPool.getClassInfosLock()) {
      myQNameToClassInfoIdMap = null;
    }
    myClassInfosIndexFile.delete();
    myDeclarationsIndexFile.delete();
  }

  private TIntIntHashMap getQNameToClassInfoIdMap() throws IOException {
    if (myQNameToClassInfoIdMap == null) {
      myQNameToClassInfoIdMap = new TIntIntHashMap();
      readIndexMap(myQNameToClassInfoIdMap, myClassInfosIndexFile);
    }
    return myQNameToClassInfoIdMap;
  }

  private TIntIntHashMap getQNameToClassDeclarationIdMap() throws IOException {
    if (myQNameToClassDeclarationIdMap == null) {
      myQNameToClassDeclarationIdMap = new TIntIntHashMap();
      readIndexMap(myQNameToClassDeclarationIdMap, myDeclarationsIndexFile);
    }
    return myQNameToClassDeclarationIdMap;
  }

  public void removeClass(final int qName) throws CacheCorruptedException {
    try {
      final int classDeclarationId = getClassDeclarationId(qName);
      if (classDeclarationId != UNKNOWN) {
        final int[] fieldIds = getFieldIds(classDeclarationId);
        synchronized (myViewPool.getFieldDeclarationsLock()) {
          for (int fieldId : fieldIds) {
            final FieldDeclarationView fieldDeclarationView = myViewPool.getFieldDeclarationView(fieldId);
            myViewPool.removeFieldDeclarationRecord(fieldDeclarationView);
          }
        }
        final int[] methodIds = getMethodIds(classDeclarationId);
        synchronized (myViewPool.getMethodDeclarationsLock()) {
          for (int methodId : methodIds) {
            final MethodDeclarationView methodDeclarationView = myViewPool.getMethodDeclarationView(methodId);
            myViewPool.removeMethodDeclarationRecord(methodDeclarationView);
          }
        }

        synchronized (myViewPool.getClassDeclarationsLock()) {
          myViewPool.removeClassDeclarationRecord(myViewPool.getClassDeclarationView(classDeclarationId));
          getQNameToClassDeclarationIdMap().remove(qName);
        }
      }

      final int classId = getClassId(qName);
      if (classId != UNKNOWN) {
        synchronized (myViewPool.getClassInfosLock()) {
          final ClassInfoView classInfoView = myViewPool.getClassInfoView(classId);
          //classInfoView.removeRecord();
          myViewPool.removeClassInfoRecord(classInfoView);
          getQNameToClassInfoIdMap().remove(qName);
        }
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }
}
