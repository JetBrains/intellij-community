/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * created at Jan 7, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.DependencyProcessor;
import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.rmi.Remote;
import java.util.*;

public class DependencyCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.DependencyCache");

  private Cache myCache;
  private Cache myNewClassesCache;

  private static final String REMOTE_INTERFACE_NAME = Remote.class.getName();
  private TIntHashSet myToUpdate = new TIntHashSet(); // qName strings to be updated.
  private final TIntHashSet myTraverseRoots = new TIntHashSet(); // Dependencies are calculated from these clasess
  private final TIntHashSet myClassesWithSourceRemoved = new TIntHashSet();
  private final TIntHashSet myPreviouslyRemoteClasses = new TIntHashSet(); // classes that were Remote, but became non-Remote for some reason
  private final TIntHashSet myMarkedInfos = new TIntHashSet(); // classes to be recompiled
  private final Set<VirtualFile> myMarkedFiles = new HashSet<VirtualFile>();
  
  private DependencyCacheNavigator myCacheNavigator;
  private SymbolTable mySymbolTable;
  private final String mySymbolTableFilePath;
  private final String myStoreDirectoryPath;
  @NonNls private static final String SYMBOLTABLE_FILE_NAME = "symboltable.dat";

  public DependencyCache(@NonNls String storeDirectoryPath) {
    myStoreDirectoryPath = storeDirectoryPath;
    LOG.assertTrue(myStoreDirectoryPath != null);

    mySymbolTableFilePath = myStoreDirectoryPath + "/" + SYMBOLTABLE_FILE_NAME;
  }

  public DependencyCacheNavigator getCacheNavigator() throws CacheCorruptedException {
    if (myCacheNavigator == null) {
      myCacheNavigator = new DependencyCacheNavigator(getCache());
    }
    return myCacheNavigator;
  }

  public void wipe() throws CacheCorruptedException {
    getCache().wipe();
    getNewClassesCache().wipe();
  }

  public Cache getCache() throws CacheCorruptedException {
    try {
      if (myCache == null) {
        // base number of cached record views of each type
        myCache = new Cache(myStoreDirectoryPath, 512);
      }

      return myCache;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public Cache getNewClassesCache() throws CacheCorruptedException {
    try {
      if (myNewClassesCache == null) {
        myNewClassesCache = new Cache(myStoreDirectoryPath + "/tmp", 16);
      }
      return myNewClassesCache;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public void addTraverseRoot(int qName) {
    myTraverseRoots.add(qName);
  }

  public void clearTraverseRoots() {
    myTraverseRoots.clear();
  }

  public boolean hasUnprocessedTraverseRoots() {
    return !myTraverseRoots.isEmpty();
  }

  public void markSourceRemoved(int qName) {
    myClassesWithSourceRemoved.add(qName);
  }

  public void addClassToUpdate(int qName) {
    myToUpdate.add(qName);
  }

  public int reparseClassFile(@NotNull File file, final byte[] fileContent) throws ClsFormatException, CacheCorruptedException {
    SymbolTable symbolTable = getSymbolTable();

    final int qName = getNewClassesCache().importClassInfo(new ClassFileReader(file, symbolTable, fileContent), symbolTable);
    addClassToUpdate(qName);
    addTraverseRoot(qName);
    return qName;
  }

  // for profiling purposes
  /*
  private static void pause() {
    System.out.println("PAUSED. ENTER A CHAR.");
    byte[] buf = new byte[1];
    try {
      System.in.read(buf);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
  */

  public void update() throws CacheCorruptedException {
    if (myToUpdate.isEmpty()) {
      return; // optimization
    }

    //pause();

    final int[] namesToUpdate = myToUpdate.toArray();
    final Cache cache = getCache();
    final Cache newCache = getNewClassesCache();
    final DependencyCacheNavigator navigator = getCacheNavigator();

    // remove unnecesary dependencies
    for (final int qName : namesToUpdate) {
      // process use-dependencies
      for (int referencedClassQName : cache.getReferencedClasses(qName)) {
        if (!cache.containsClass(referencedClassQName)) {
          continue;
        }
        cache.removeClassReferencer(referencedClassQName, qName);
      }
      cache.clearReferencedClasses(qName);
      // process inheritance dependencies
      navigator.walkSuperClasses(qName, new ClassInfoProcessor() {
        public boolean process(int classQName) throws CacheCorruptedException {
          cache.removeSubclass(classQName, qName);
          return true;
        }
      });
    }

    // do update of classInfos
    for (final int qName : namesToUpdate) {
      cache.importClassInfo(newCache, qName);
    }

    // build forward-dependencies for the new infos, all new class infos must be already in the main cache!

    final SymbolTable symbolTable = getSymbolTable();

    for (final int qName : namesToUpdate) {
      if (!newCache.containsClass(qName)) {
        continue;
      }
      buildForwardDependencies(qName, newCache.getReferences(qName));
      boolean isRemote = false;
      // "remote objects" are classes that _directly_ implement remote interfaces
      final int[] superInterfaces = cache.getSuperInterfaces(qName);
      if (superInterfaces.length > 0) {
        final int remoteInterfaceName = symbolTable.getId(REMOTE_INTERFACE_NAME);
        for (int superInterface : superInterfaces) {
          if (isRemoteInterface(cache, superInterface, remoteInterfaceName)) {
            isRemote = true;
            break;
          }
        }
      }
      final boolean wasRemote = cache.isRemote(qName);
      if (wasRemote && !isRemote) {
        synchronized (myPreviouslyRemoteClasses) {
          myPreviouslyRemoteClasses.add(qName);
        }
      }
      cache.setRemote(qName, isRemote);
    }

    // building subclass dependencies
    for (final int qName : namesToUpdate) {
      buildSubclassDependencies(getCache(), qName, qName);
    }

    for (final int qName : myClassesWithSourceRemoved.toArray()) {
      cache.removeClass(qName);
    }
    myToUpdate = new TIntHashSet();

    //pause();
  }

  private void buildForwardDependencies(final int classQName, final Collection<ReferenceInfo> references) throws CacheCorruptedException {
    final Cache cache = getCache();

    final int genericSignature = cache.getGenericSignature(classQName);
    if (genericSignature != -1) {
      final String genericClassSignature = resolve(genericSignature);
      final int[] bounds = findBounds(genericClassSignature);
      for (int boundClassQName : bounds) {
        cache.addClassReferencer(boundClassQName, classQName);
      }
    }

    buildAnnotationDependencies(classQName, cache.getRuntimeVisibleAnnotations(classQName));
    buildAnnotationDependencies(classQName, cache.getRuntimeInvisibleAnnotations(classQName));

    for (final ReferenceInfo refInfo : references) {
      final int declaringClassName = getActualDeclaringClassForReference(refInfo);
      if (declaringClassName == Cache.UNKNOWN) {
        continue;
      }
      if (refInfo instanceof MemberReferenceInfo) {
        final MemberInfo memberInfo = ((MemberReferenceInfo)refInfo).getMemberInfo();
        if (memberInfo instanceof FieldInfo) {
          cache.addFieldReferencer(declaringClassName, memberInfo.getName(), classQName);
        }
        else if (memberInfo instanceof MethodInfo) {
          cache.addMethodReferencer(declaringClassName, memberInfo.getName(), memberInfo.getDescriptor(), classQName);
        }
        else {
          LOG.error("Unknown member info class: " + memberInfo.getClass().getName());
        }
      }
      else { // reference to class
        cache.addClassReferencer(declaringClassName, classQName);
      }
    }
    final SymbolTable symbolTable = getSymbolTable();

    for (final FieldInfo fieldInfo : cache.getFields(classQName)) {
      buildAnnotationDependencies(classQName, fieldInfo.getRuntimeVisibleAnnotations());
      buildAnnotationDependencies(classQName, fieldInfo.getRuntimeInvisibleAnnotations());

      String className = MakeUtil.parseObjectType(symbolTable.getSymbol(fieldInfo.getDescriptor()), 0);
      if (className == null) {
        continue;
      }
      final int cls = symbolTable.getId(className);
      cache.addClassReferencer(cls, classQName);
    }

    for (final MethodInfo methodInfo : cache.getMethods(classQName)) {
      buildAnnotationDependencies(classQName, methodInfo.getRuntimeVisibleAnnotations());
      buildAnnotationDependencies(classQName, methodInfo.getRuntimeInvisibleAnnotations());
      buildAnnotationDependencies(classQName, methodInfo.getRuntimeVisibleParameterAnnotations());
      buildAnnotationDependencies(classQName, methodInfo.getRuntimeInvisibleParameterAnnotations());

      if (methodInfo.isConstructor()) {
        continue;
      }

      final String returnTypeClassName = MakeUtil.parseObjectType(methodInfo.getReturnTypeDescriptor(symbolTable), 0);
      if (returnTypeClassName != null) {
        final int returnTypeClassQName = symbolTable.getId(returnTypeClassName);
        cache.addClassReferencer(returnTypeClassQName, classQName);
      }

      String[] parameterSignatures = CacheUtils.getParameterSignatures(methodInfo, symbolTable);
      for (String parameterSignature : parameterSignatures) {
        String paramClassName = MakeUtil.parseObjectType(parameterSignature, 0);
        if (paramClassName != null) {
          final int paramClassId = symbolTable.getId(paramClassName);
          cache.addClassReferencer(paramClassId, classQName);
        }
      }
    }
  }

  private static boolean isRemoteInterface(Cache cache, int ifaceName, final int remoteInterfaceName) throws CacheCorruptedException {
    if (ifaceName == remoteInterfaceName) {
      return true;
    }
    for (int superInterfaceName : cache.getSuperInterfaces(ifaceName)) {
      if (isRemoteInterface(cache, superInterfaceName, remoteInterfaceName)) {
        return true;
      }
    }
    return false;
  }


  private void buildAnnotationDependencies(int classQName, AnnotationConstantValue[][] annotations) throws CacheCorruptedException {
    if (annotations == null || annotations.length == 0) {
      return;
    }
    for (AnnotationConstantValue[] annotation : annotations) {
      buildAnnotationDependencies(classQName, annotation);
    }
  }

  private void buildAnnotationDependencies(int classQName, AnnotationConstantValue[] annotations) throws CacheCorruptedException {
    if (annotations == null || annotations.length == 0) {
      return;
    }
    final Cache cache = getCache();
    for (AnnotationConstantValue annotation : annotations) {
      final int annotationQName = annotation.getAnnotationQName();

      cache.addClassReferencer(annotationQName, classQName);

      final AnnotationNameValuePair[] memberValues = annotation.getMemberValues();
      for (final AnnotationNameValuePair nameValuePair : memberValues) {
        for (MethodInfo annotationMember : cache.findMethodsByName(annotationQName, nameValuePair.getName())) {
          cache.addMethodReferencer(annotationQName, annotationMember.getName(), annotationMember.getDescriptor(), classQName);
        }
      }
    }
  }

  private int[] findBounds(final String genericClassSignature) throws CacheCorruptedException{
    try {
      final String[] boundInterfaces = BoundsParser.getBounds(genericClassSignature);
      int[] ids = ArrayUtil.newIntArray(boundInterfaces.length);
      for (int i = 0; i < boundInterfaces.length; i++) {
        ids[i] = getSymbolTable().getId(boundInterfaces[i]);
      }
      return ids;
    }
    catch (SignatureParsingException e) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
  }

  // fixes JDK 1.4 javac bug that generates references in the constant pool
  // to the subclass even if the field was declared in a superclass
  private int getActualDeclaringClassForReference(final ReferenceInfo refInfo) throws CacheCorruptedException {
    if (!(refInfo instanceof MemberReferenceInfo)) {
      return refInfo.getClassName();
    }
    final int declaringClassName = refInfo.getClassName();
    final Cache cache = getCache();
    final MemberInfo memberInfo = ((MemberReferenceInfo)refInfo).getMemberInfo();
    if (memberInfo instanceof FieldInfo) {
      if (cache.findFieldByName(declaringClassName, memberInfo.getName()) != null) {
        return declaringClassName;
      }
    }
    else if (memberInfo instanceof MethodInfo) {
      if (cache.findMethod(declaringClassName, memberInfo.getName(), memberInfo.getDescriptor()) != null) {
        return declaringClassName;
      }
    }
    final DeclaringClassFinder finder = new DeclaringClassFinder(memberInfo);
    getCacheNavigator().walkSuperClasses(declaringClassName, finder);
    return finder.getDeclaringClassName();
  }

  /**
   * @return qualified names of the classes that should be additionally recompiled
   */
  public Pair<int[], Set<VirtualFile>> findDependentClasses(CompileContext context, Project project, Set<VirtualFile> successfullyCompiled)
    throws CacheCorruptedException {

    markDependencies(context, project, successfullyCompiled);
    return new Pair<int[], Set<VirtualFile>>(myMarkedInfos.toArray(), Collections.unmodifiableSet(myMarkedFiles));
  }

  private void markDependencies(CompileContext context, Project project, final Set<VirtualFile> successfullyCompiled) throws CacheCorruptedException {
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("====================Marking dependent files=====================");
      }
      // myToUpdate can be modified during the mark procedure, so use toArray() to iterate it
      final int[] traverseRoots = myTraverseRoots.toArray();
      final SourceFileFinder sourceFileFinder = new SourceFileFinder(project, context);
      final CachingSearcher searcher = new CachingSearcher(project);
      final ChangedRetentionPolicyDependencyProcessor changedRetentionPolicyDependencyProcessor = new ChangedRetentionPolicyDependencyProcessor(project, searcher, this);
      for (final int qName : traverseRoots) {
        if (!getCache().containsClass(qName)) {
          continue;
        }
        if (getNewClassesCache().containsClass(qName)) { // there is a new class file created
          new JavaDependencyProcessor(project, this, qName).run();
          ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo> changed =
            new ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo>();
          ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo> removed =
            new ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo>();
          findModifiedConstants(qName, changed, removed);
          if (!changed.isEmpty() || !removed.isEmpty()) {
            new ChangedConstantsDependencyProcessor(
              project, searcher, this, qName, context.getProgressIndicator().isCanceled(),
              changed.toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[changed.size()]),
              removed.toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[removed.size()])
            ).run();
          }
          changedRetentionPolicyDependencyProcessor.checkAnnotationRetentionPolicyChanges(qName);
          for (DependencyProcessor additionalProcessor : DependencyProcessor.EXTENSION_POINT_NAME.getExtensions()) {
            additionalProcessor.processDependencies(context, qName);
          }
        }
        else {
          boolean isSourceDeleted = false;
          if (myClassesWithSourceRemoved.contains(qName)) { // no recompiled class file, check whether the classfile exists
            isSourceDeleted = true;
          }
          else if (!new File(getCache().getPath(qName)).exists()) {
            final String qualifiedName = resolve(qName);
            final String sourceFileName = getCache().getSourceFileName(qName);
            final boolean markAsRemovedSource = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
              public Boolean compute() {
                VirtualFile sourceFile = sourceFileFinder.findSourceFile(qualifiedName, sourceFileName);
                return sourceFile == null || successfullyCompiled.contains(sourceFile) ? Boolean.TRUE : Boolean.FALSE;
              }
            }).booleanValue();
            if (markAsRemovedSource) {
              // for Inner classes: sourceFile may exist, but the inner class declaration inside it may not,
              // thus the source for the class info should be considered removed
              isSourceDeleted = true;
              markSourceRemoved(qName);
              myMarkedInfos.remove(qName); // if the info has been marked already, the mark should be removed
            }
          }
          if (isSourceDeleted) {
            Dependency[] backDependencies = getCache().getBackDependencies(qName);
            for (Dependency backDependency : backDependencies) {
              if (markTargetClassInfo(backDependency)) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug(
                    "Mark dependent class " + backDependency.getClassQualifiedName() + "; reason: no class file found for " + qName);
                }
              }
            }
          }
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("================================================================");
      }
    }
    catch (ProcessCanceledException ignored) {
      // deliberately suppressed
    }
  }

  private void findModifiedConstants(
    final int qName,
    Collection<ChangedConstantsDependencyProcessor.FieldChangeInfo> changedConstants,
    Collection<ChangedConstantsDependencyProcessor.FieldChangeInfo> removedConstants) throws CacheCorruptedException {

    final Cache cache = getCache();
    for (final FieldInfo field : cache.getFields(qName)) {
      final int oldFlags = field.getFlags();
      if (ClsUtil.isStatic(oldFlags) && ClsUtil.isFinal(oldFlags)) {
        final Cache newClassesCache = getNewClassesCache();
        FieldInfo newField = newClassesCache.findFieldByName(qName, field.getName());
        if (newField == null) {
          if (!ConstantValue.EMPTY_CONSTANT_VALUE.equals(field.getConstantValue())) {
            // if the field was really compile time constant
            removedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(field));
          }
        }
        else {
          final boolean visibilityRestricted = MakeUtil.isMoreAccessible(oldFlags, newField.getFlags());
          if (!field.getConstantValue().equals(newField.getConstantValue()) || visibilityRestricted) {
            changedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(field, visibilityRestricted));
          }
        }
      }
    }
  }

  private static void buildSubclassDependencies(Cache cache, final int qName, int targetClassId) throws CacheCorruptedException {
    final int superQName = cache.getSuperQualifiedName(targetClassId);
    if (superQName != Cache.UNKNOWN) {
      cache.addSubclass(superQName, qName);
      buildSubclassDependencies(cache, qName, superQName);
    }

    int[] interfaces = cache.getSuperInterfaces(targetClassId);
    for (final int interfaceName : interfaces) {
      cache.addSubclass(interfaceName, qName);
      buildSubclassDependencies(cache, qName, interfaceName);
    }
  }


  /**
   * Marks ClassInfo targeted by the dependency
   * @return true if really added, false otherwise
   */
  public boolean markTargetClassInfo(Dependency dependency) throws CacheCorruptedException {
    return markClassInfo(dependency.getClassQualifiedName(), false);
  }

  /**
   * Marks ClassInfo that corresponds to the specified qualified name
   * If class info is already recompiled, it is not marked
   * @return true if really added, false otherwise
   */
  public boolean markClass(int qualifiedName) throws CacheCorruptedException {
    return markClass(qualifiedName, false);
  }

  /**
   * Marks ClassInfo that corresponds to the specified qualified name
   * If class info is already recompiled, it is not marked unless force parameter is true
   * @return true if really added, false otherwise
   */
  public boolean markClass(int qualifiedName, boolean force) throws CacheCorruptedException {
    return markClassInfo(qualifiedName, force);
  }

  public boolean isTargetClassInfoMarked(Dependency dependency) {
    return isClassInfoMarked(dependency.getClassQualifiedName());
  }

  public boolean isClassInfoMarked(int qName) {
    return myMarkedInfos.contains(qName);
  }
  
  public void markFile(VirtualFile file) {
    myMarkedFiles.add(file);
  }

  /**
   * @return true if really marked, false otherwise
   */
  private boolean markClassInfo(int qName, boolean force) throws CacheCorruptedException {
    if (!getCache().containsClass(qName)) {
      return false;
    }
    if (myClassesWithSourceRemoved.contains(qName)) {
      return false; // no need to recompile since source has been removed
    }
    if (!force) {
      if (getNewClassesCache().containsClass(qName)) { // already recompiled
        return false;
      }
    }
    return myMarkedInfos.add(qName);
  }

  public void resetState() {
    myClassesWithSourceRemoved.clear();
    myMarkedFiles.clear();
    myMarkedInfos.clear();
    myToUpdate.clear();
    myTraverseRoots.clear();
    if (myNewClassesCache != null) {
      myNewClassesCache.wipe();
      myNewClassesCache = null;
    }
    myCacheNavigator = null;
    try {
      if (myCache != null) {
        myCache.dispose();
        myCache = null;
      }
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
    }
    try {
      if (mySymbolTable != null) {
        mySymbolTable.dispose();
        mySymbolTable = null;
      }
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
    }
  }


  public SymbolTable getSymbolTable() throws CacheCorruptedException {
    if (mySymbolTable == null) {
      mySymbolTable = new SymbolTable(new File(mySymbolTableFilePath));
    }
    return mySymbolTable;
  }

  public String resolve(int id) throws CacheCorruptedException {
    return getSymbolTable().getSymbol(id);
  }

  public boolean wasRemote(int qName) {
    return myPreviouslyRemoteClasses.contains(qName);
  }

  private class DeclaringClassFinder implements ClassInfoProcessor {
    private final int myMemberName;
    private final int myMemberDescriptor;
    private int myDeclaringClass = Cache.UNKNOWN;
    private final boolean myIsField;

    private DeclaringClassFinder(MemberInfo memberInfo) {
      myMemberName = memberInfo.getName();
      myMemberDescriptor = memberInfo.getDescriptor();
      myIsField = memberInfo instanceof FieldInfo;
    }

    public int getDeclaringClassName() {
      return myDeclaringClass;
    }

    public boolean process(int classQName) throws CacheCorruptedException {
      final Cache cache = getCache();
      if (myIsField) {
        final FieldInfo fieldId = cache.findField(classQName, myMemberName, myMemberDescriptor);
        if (fieldId != null) {
          myDeclaringClass = classQName;
          return false;
        }
      }
      else {
        final MethodInfo methodId = cache.findMethod(classQName, myMemberName, myMemberDescriptor);
        if (methodId != null) {
          myDeclaringClass = classQName;
          return false;
        }
      }
      return true;
    }
  }
}
