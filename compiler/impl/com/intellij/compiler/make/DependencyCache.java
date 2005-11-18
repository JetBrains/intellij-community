/**
 * created at Jan 7, 2002
 * @author Jeka
 */
package com.intellij.compiler.make;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.rmi.Remote;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class DependencyCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.DependencyCache");

  private Cache myCache;
  private Cache myNewClassesCache;

  private static final String REMOTE_INTERFACE_NAME = Remote.class.getName();
  private final TIntHashSet myClassesWithSuperlistChanged = new TIntHashSet();
  private TIntHashSet myToUpdate = new TIntHashSet(); // qName strings to be updated.
  private final TIntHashSet myTraverseRoots = new TIntHashSet(); // Dependencies are calculated from these clasess
  private final TIntHashSet myClassesWithSourceRemoved = new TIntHashSet();
  private final TIntHashSet myPreviouslyRemoteClasses = new TIntHashSet(); // classes that were Remote, but became non-Remote for some reason
  private TIntHashSet myMarkedInfos = new TIntHashSet(); // classes to be recompiled

  private DependencyCacheNavigator myCacheNavigator;
  private SymbolTable mySymbolTable;
  private final String mySymbolTableFilePath;
  private final String myStoreDirectoryPath;
  private static final @NonNls String SYMBOLTABLE_FILE_NAME = "symboltable.dat";

  public DependencyCache(String storeDirectoryPath) {
    myStoreDirectoryPath = storeDirectoryPath;
    LOG.assertTrue(myStoreDirectoryPath != null);

    mySymbolTableFilePath = myStoreDirectoryPath + "/" + SYMBOLTABLE_FILE_NAME;
  }


  public DependencyCacheNavigator getCacheNavigator() throws CacheCorruptedException {
    if (myCacheNavigator == null) {
      myCacheNavigator = new DependencyCacheNavigator(getCache(), this);
    }
    return myCacheNavigator;
  }

  public void wipe() throws CacheCorruptedException {
    getCache().wipe();
    getNewClassesCache().wipe();
  }

  public Cache getCache() throws CacheCorruptedException {
    if (myCache == null) {
      myCache = new Cache(myStoreDirectoryPath, 50, Integer.MAX_VALUE);
    }

    return myCache;
  }

  public Cache getNewClassesCache() throws CacheCorruptedException {
    if (myNewClassesCache == null) {
      //noinspection HardCodedStringLiteral
      myNewClassesCache = new Cache(myStoreDirectoryPath + "/tmp", 50, 1000);
    }
    return myNewClassesCache;
  }

  public void addTraverseRoot(int qName) {
    myTraverseRoots.add(qName);
  }

  public void markSourceRemoved(int qName) {
    myClassesWithSourceRemoved.add(qName);
  }

  public void addClassToUpdate(int qName) {
    myToUpdate.add(qName);
  }

  public int reparseClassFile(File file) throws ClsFormatException, CacheCorruptedException {
    SymbolTable symbolTable = getSymbolTable();

    final int qName = getNewClassesCache().importClassInfo(new ClassFileReader(file, symbolTable), symbolTable);
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
    if (myToUpdate.size() == 0) {
      return; // optimization
    }

    //final long updateStart = System.currentTimeMillis();
    //pause();

    final int[] namesToUpdate = myToUpdate.toArray();
    final Cache cache = getCache();
    final DependencyCacheNavigator navigator = getCacheNavigator();

    // remove unnecesary dependencies
    for (final int qName : namesToUpdate) {
      final int oldClassId = cache.getClassId(qName);
      if (oldClassId != Cache.UNKNOWN) {
        // process use-dependencies
        final int[] referencedClasses = cache.getReferencedClasses(oldClassId);
        for (int referencedClass : referencedClasses) {
          final int referencedClassDeclarationId = cache.getClassDeclarationId(referencedClass);
          if (referencedClassDeclarationId == Cache.UNKNOWN) {
            continue;
          }

          cache.removeClassReferencer(referencedClassDeclarationId, qName);

          final int[] fieldIds = cache.getFieldIds(referencedClassDeclarationId);
          for (int fieldId : fieldIds) {
            cache.removeFieldReferencer(fieldId, qName);
          }

          final int[] methodIds = cache.getMethodIds(referencedClassDeclarationId);
          for (int methodId : methodIds) {
            cache.removeMethodReferencer(methodId, qName);
          }
        }
        // process inheritance dependencies
        navigator.walkSuperClasses(qName, new ClassInfoProcessor() {
          public boolean process(int classQName) throws CacheCorruptedException {
            final int classId = cache.getClassId(classQName);
            cache.removeSubclass(classId, qName);
            return true;
          }
        });
      }
    }

    // do update of classInfos
    for (final int qName : namesToUpdate) {
      final int newInfoId = getNewClassesCache().getClassId(qName);
      if (newInfoId == Cache.UNKNOWN) {
        continue; // no member data to update
      }
      cache.importClassInfo(getNewClassesCache(), qName);
    }

    // build forward-dependencies for the new infos, all new class infos must be already in the main cache!

    final SymbolTable symbolTable = getSymbolTable();
    for (final int qName : namesToUpdate) {
      final int newClassId = getNewClassesCache().getClassId(qName);
      if (newClassId == Cache.UNKNOWN) {
        continue;
      }
      buildForwardDependencies(qName, getNewClassesCache().getReferences(newClassId));

      boolean isRemote = false;
      final int classId = cache.getClassId(qName);
      // "remote objects" are classes that _directly_ implement remote interfaces
      final int[] superInterfaces = cache.getSuperInterfaces(classId);
      if (superInterfaces.length > 0) {
        final int remoteInterfaceName = symbolTable.getId(REMOTE_INTERFACE_NAME);
        for (int superInterface : superInterfaces) {
          if (isRemoteInterface(cache, superInterface, remoteInterfaceName)) {
            isRemote = true;
            break;
          }
        }
      }
      final boolean wasRemote = cache.isRemote(classId);
      if (wasRemote && !isRemote) {
        myPreviouslyRemoteClasses.add(qName);
      }
      cache.setRemote(classId, isRemote);
    }

    // build back-dependencies

    for (final int qName : namesToUpdate) {
      buildSubclassDependencies(qName, qName);
    }

    final int[] classesToRemove = myClassesWithSourceRemoved.toArray();
    for (final int qName : classesToRemove) {
      cache.removeClass(qName);
    }

    myToUpdate = new TIntHashSet();

    //System.out.println("Dependency cache update took: " + (System.currentTimeMillis() - updateStart) + " ms");
    //pause();
  }

  private void buildForwardDependencies(final int classQName, final ReferenceInfo[] references) throws CacheCorruptedException {
    final Cache cache = getCache();
    final int classId = cache.getClassId(classQName);

    final int genericSignature = cache.getGenericSignature(classId);
    if (genericSignature != -1) {
      final String genericClassSignature = resolve(genericSignature);
      final int[] bounds = findBounds(genericClassSignature);
      for (int idx = 0; idx < bounds.length; idx++) {
        int boundClassQName = bounds[idx];
        cache.addClassReferencer(cache.getClassDeclarationId(boundClassQName), classQName);
        cache.addReferencedClass(classId, boundClassQName);
      }
    }

    buildAnnotationDependencies(classQName, cache.getRuntimeVisibleAnnotations(classId));
    buildAnnotationDependencies(classQName, cache.getRuntimeInvisibleAnnotations(classId));

    for (int idx = 0; idx < references.length; idx++) {
      final ReferenceInfo refInfo = references[idx];
      final int declaringClassName = getActualDeclaringClassForReference(refInfo);
      if (declaringClassName == Cache.UNKNOWN) {
        continue;
      }
      final int declaringClassId = cache.getClassDeclarationId(declaringClassName);
      if (refInfo instanceof MemberReferenceInfo) {
        final MemberInfo memberInfo = ((MemberReferenceInfo)refInfo).getMemberInfo();
        if (memberInfo instanceof FieldInfo) {
          int fieldId = CacheUtils.findField(cache, declaringClassId, memberInfo.getName(), memberInfo.getDescriptor());
          if (fieldId == Cache.UNKNOWN) {
            fieldId = cache.putMember(declaringClassId, Cache.UNKNOWN, memberInfo);
          }
          cache.addFieldReferencer(fieldId, classQName);
        }
        else if (memberInfo instanceof MethodInfo) {
          int methodId = CacheUtils.findMethod(cache, declaringClassId, memberInfo.getName(), memberInfo.getDescriptor());
          if (methodId == Cache.UNKNOWN) {
            methodId = cache.putMember(declaringClassId, Cache.UNKNOWN, memberInfo);
          }
          cache.addMethodReferencer(methodId, classQName);
        }
        else {
          LOG.error("Unknown member info class: " + memberInfo.getClass().getName());
        }
      }
      else { // reference to class
        cache.addClassReferencer(declaringClassId, classQName);
      }
      cache.addReferencedClass(classId, declaringClassName);
    }
    final SymbolTable symbolTable = getSymbolTable();
    final int classDeclarationId = cache.getClassDeclarationId(classQName);

    final int[] fieldIds = cache.getFieldIds(classDeclarationId);
    for (int idx = 0; idx < fieldIds.length; idx++) {
      final int fieldId = fieldIds[idx];

      buildAnnotationDependencies(classQName, cache.getFieldRuntimeVisibleAnnotations(fieldId));
      buildAnnotationDependencies(classQName, cache.getFieldRuntimeInvisibleAnnotations(fieldId));

      final int signature = cache.getFieldDescriptor(fieldId);
      String className = MakeUtil.parseObjectType(symbolTable.getSymbol(signature), 0);
      if (className == null) {
        continue;
      }
      final int cls = symbolTable.getId(className);
      cache.addClassReferencer(cache.getClassDeclarationId(cls), classQName);
      cache.addReferencedClass(classId, cls);
    }

    final int[] methods = cache.getMethodIds(classDeclarationId);
    for (int idx = 0; idx < methods.length; idx++) {
      final int methodId = methods[idx];

      buildAnnotationDependencies(classQName, cache.getMethodRuntimeVisibleAnnotations(methodId));
      buildAnnotationDependencies(classQName, cache.getMethodRuntimeInvisibleAnnotations(methodId));
      buildAnnotationDependencies(classQName, cache.getMethodRuntimeVisibleParamAnnotations(methodId));
      buildAnnotationDependencies(classQName, cache.getMethodRuntimeInvisibleParamAnnotations(methodId));

      if (cache.isConstructor(methodId)) {
        continue;
      }

      final String returnTypeClassName = MakeUtil.parseObjectType(CacheUtils.getMethodReturnTypeDescriptor(cache, methodId, getSymbolTable()), 0);
      if (returnTypeClassName != null) {
        final int returnTypeClassId = symbolTable.getId(returnTypeClassName);
        cache.addClassReferencer(cache.getClassDeclarationId(returnTypeClassId), classQName);
        cache.addReferencedClass(classId, returnTypeClassId);
      }

      String[] parameterSignatures = CacheUtils.getParameterSignatures(cache, methodId, getSymbolTable());
      for (int i = 0; i < parameterSignatures.length; i++) {
        String paramClassName = MakeUtil.parseObjectType(parameterSignatures[i], 0);
        if (paramClassName != null) {
          final int paramClassId = symbolTable.getId(paramClassName);
          cache.addClassReferencer(cache.getClassDeclarationId(paramClassId), classQName);
          cache.addReferencedClass(classId, paramClassId);
        }
      }
    }

  }

  private boolean isRemoteInterface(Cache cache, int ifaceName, final int remoteInterfaceName) throws CacheCorruptedException {
    if (ifaceName == remoteInterfaceName) {
      return true;
    }
    final int[] superInterfaces = cache.getSuperInterfaces(cache.getClassId(ifaceName));
    for (int idx = 0; idx < superInterfaces.length; idx++) {
      int superInterfaceName = superInterfaces[idx];
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
    for (int idx = 0; idx < annotations.length; idx++) {
      buildAnnotationDependencies(classQName, annotations[idx]);
    }
  }

  private void buildAnnotationDependencies(int classQName, AnnotationConstantValue[] annotations) throws CacheCorruptedException {
    if (annotations == null || annotations.length == 0) {
      return;
    }
    final Cache cache = getCache();
    final int classId = cache.getClassId(classQName);
    for (int idx = 0; idx < annotations.length; idx++) {
      AnnotationConstantValue annotation = annotations[idx];
      final int annotationQName = annotation.getAnnotationQName();

      final int annotationDeclarationId = cache.getClassDeclarationId(annotationQName);

      cache.addClassReferencer(annotationDeclarationId, classQName);
      cache.addReferencedClass(classId, annotationQName);

      final AnnotationNameValuePair[] memberValues = annotation.getMemberValues();
      for (int i = 0; i < memberValues.length; i++) {
        final AnnotationNameValuePair nameValuePair = memberValues[i];
        final int[] annotationMembers = CacheUtils.findMethodsByName(cache, annotationDeclarationId, nameValuePair.getName());
        for (int j = 0; j < annotationMembers.length; j++) {
          int annotationMember = annotationMembers[j];
          cache.addMethodReferencer(annotationMember, classQName);
        }
      }
    }
  }

  public int[] findBounds(final String genericClassSignature) throws CacheCorruptedException{
    try {
      final String[] boundInterfaces = BoundsParser.getBounds(genericClassSignature);
      int[] ids = new int[boundInterfaces.length];
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
  public int getActualDeclaringClassForReference(final ReferenceInfo refInfo) throws CacheCorruptedException {
    if (!(refInfo instanceof MemberReferenceInfo)) {
      return refInfo.getClassName();
    }
    final int declaringClassName = refInfo.getClassName();
    if (getCache().getClassId(declaringClassName) == Cache.UNKNOWN) {
      return declaringClassName;
    }
    final int classDeclarationId = getCache().getClassDeclarationId(declaringClassName);
    final MemberInfo memberInfo = ((MemberReferenceInfo)refInfo).getMemberInfo();
    if (memberInfo instanceof FieldInfo) {
      if (CacheUtils.findFieldByName(getCache(), classDeclarationId, memberInfo.getName()) != Cache.UNKNOWN) {
        return declaringClassName;
      }
    }
    else if (memberInfo instanceof MethodInfo) {
      if (CacheUtils.findMethod(getCache(), classDeclarationId, memberInfo.getName(), memberInfo.getDescriptor()) != Cache.UNKNOWN) {
        return declaringClassName;
      }
    }
    final DeclaringClassFinder finder = new DeclaringClassFinder(memberInfo);
    getCacheNavigator().walkSuperClasses(declaringClassName, finder);
    final int className = finder.getDeclaringClassName();
    return className;
    //return className != Cache.UNKNOWN? className : declaringClassName;
  }


  /**
   * @return qualified names of the classes that should be additionally recompiled
   */
  public int[] findDependentClasses(CompileContext context, Project project, Set successfullyCompiled) throws CacheCorruptedException {
    markDependencies(context, project, successfullyCompiled);
    return myMarkedInfos.toArray();
  }

  private void markDependencies(CompileContext context, Project project, final Set successfullyCompiled) throws CacheCorruptedException {
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("====================Marking dependent files=====================");
      }
      // myToUpdate can be modified during the mark procedure, so use toArray() to iterate it
      int[] qNamesToUpdate = myTraverseRoots.toArray();
      final SourceFileFinder sourceFileFinder = new SourceFileFinder(project, context);
      final CachingSearcher searcher = new CachingSearcher(project);
      final ChangedRetentionPolicyDependencyProcessor changedRetentionPolicyDependencyProcessor = new ChangedRetentionPolicyDependencyProcessor(project, searcher, this);
      for (int nameIndex = 0; nameIndex < qNamesToUpdate.length; nameIndex++) {
        int qName = qNamesToUpdate[nameIndex];
        int oldInfoId = getCache().getClassId(qName);
        if (oldInfoId == Cache.UNKNOWN) {
          continue;
        }
        int newInfoId = getNewClassesCache().getClassId(qName);
        if (newInfoId != Cache.UNKNOWN) { // there is a new class file created
          new DependencyProcessor(project, this, qName).run();
          ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo> changed = new ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo>();
          ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo> removed = new ArrayList<ChangedConstantsDependencyProcessor.FieldChangeInfo>();
          findModifiedConstants(qName, changed, removed);
          if (changed.size() > 0 || removed.size() > 0) {
            new ChangedConstantsDependencyProcessor(
              project, searcher, this, qName,
              changed.toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[changed.size()]),
              removed.toArray(new ChangedConstantsDependencyProcessor.FieldChangeInfo[removed.size()])
            ).run();
          }
          changedRetentionPolicyDependencyProcessor.checkAnnotationRetentionPolicyChanges(qName);
        }
        else {
          boolean isSourceDeleted = false;
          if (myClassesWithSourceRemoved.contains(qName)){ // no recompiled class file, check whether the classfile exists
            isSourceDeleted = true;
          }
          else if (!new File(getCache().getPath(oldInfoId)).exists()) {
            final String qualifiedName = resolve(qName);
            final String sourceFileName = getCache().getSourceFileName(oldInfoId);
            final boolean markAsRemovedSource = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
              public Boolean compute() {
                VirtualFile sourceFile = sourceFileFinder.findSourceFile(qualifiedName, sourceFileName);
                return (sourceFile == null || successfullyCompiled.contains(sourceFile))? Boolean.TRUE : Boolean.FALSE;
              }
            }).booleanValue();
            if (markAsRemovedSource) {
              // for Inner classes: sourceFile may exist, but the inner class declaration in it - not, thus the source for the class info should be considered removed
              isSourceDeleted = true;
              markSourceRemoved(qName);
              myMarkedInfos.remove(qName); // if the info has been marked already, the mark should be removed
            }
          }
          if (isSourceDeleted) {
            Dependency[] backDependencies = getCache().getBackDependencies(qName);
            for (int idx = 0; idx < backDependencies.length; idx++) {
              if (markTargetClassInfo(backDependencies[idx])) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug("Mark dependent class "+backDependencies[idx].getClassQualifiedName() + "; reason: no class file found for " + qName);
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

    int[] fields = getCache().getFieldIds(getCache().getClassDeclarationId(qName));
    for (int idx = 0; idx < fields.length; idx++) {
      final int field = fields[idx];
      final int oldFlags = getCache().getFieldFlags(field);
      if (ClsUtil.isStatic(oldFlags) && ClsUtil.isFinal(oldFlags)) {
        int newField = CacheUtils.findFieldByName(getNewClassesCache(), getNewClassesCache().getClassDeclarationId(qName), getCache().getFieldName(field));
        if (newField == Cache.UNKNOWN) {
          if (!ConstantValue.EMPTY_CONSTANT_VALUE.equals(getCache().getFieldConstantValue(field))) { // if the field was really compile time constant
            removedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(getCache().createFieldInfo(field)));
          }
        }
        else {
          final boolean visibilityRestricted = MakeUtil.isMoreAccessible(oldFlags, getNewClassesCache().getFieldFlags(newField));
          if (!getCache().getFieldConstantValue(field).equals(getNewClassesCache().getFieldConstantValue(newField)) || visibilityRestricted) {
            changedConstants.add(new ChangedConstantsDependencyProcessor.FieldChangeInfo(getCache().createFieldInfo(field), visibilityRestricted));
          }
        }
      }
    }
  }

  private void buildSubclassDependencies(final int qName, int targetClassQName) throws CacheCorruptedException {
    final int targetClassId = getCache().getClassId(targetClassQName);

    final int superQName = getCache().getSuperQualifiedName(targetClassId);
    if (superQName != Cache.UNKNOWN) {
      int superClassId = getCache().getClassId(superQName);
      if (superClassId != Cache.UNKNOWN) {
        getCache().addSubclass(superClassId, qName);
        buildSubclassDependencies(qName, superQName);
      }
    }

    int[] interfaces = getCache().getSuperInterfaces(targetClassId);
    for (int idx = 0; idx < interfaces.length; idx++) {
      final int interfaceName = interfaces[idx];
      int superId = getCache().getClassId(interfaceName);
      if (superId != Cache.UNKNOWN) {
        getCache().addSubclass(superId, qName);
        buildSubclassDependencies(qName, interfaceName);
      }
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

  public boolean isTargetClassInfoMarked(Dependency dependency) throws CacheCorruptedException {
    return isClassInfoMarked(dependency.getClassQualifiedName());
  }

  public boolean isClassInfoMarked(int qName) throws CacheCorruptedException {
    return myMarkedInfos.contains(qName);
  }

  /**
   * @return true if really marked, false otherwise
   */
  private boolean markClassInfo(int qName, boolean force) throws CacheCorruptedException {
    if (getCache().getClassId(qName) == Cache.UNKNOWN) {
      return false;
    }
    if (myClassesWithSourceRemoved.contains(qName)) {
      return false; // no need to recompile since source has been removed
    }
    if (!force) {
      if (getNewClassesCache().getClassId(qName) != Cache.UNKNOWN) { // already recompiled
        return false;
      }
    }
    return myMarkedInfos.add(qName);
  }

  public void dispose() {
    if (myNewClassesCache != null) {
      myNewClassesCache.wipe();
    }
    if (myCache != null) {
      myCache.dispose();
    }
    try {
      if (mySymbolTable != null) {
        // important: compact symbol table only after all caches and indices are updated
        // [jeka] switched off
        //compactSymbolTable(mySymbolTable);
        final File symbolTableFile = new File(mySymbolTableFilePath);
        symbolTableFile.createNewFile();

        DataOutputStream symTableStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(symbolTableFile)));
        try {
          mySymbolTable.save(symTableStream);
        }
        finally {
          symTableStream.close();
          mySymbolTable = null;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e); // todo
    }
  }


  public SymbolTable getSymbolTable() throws CacheCorruptedException {
    if (mySymbolTable == null) {
      mySymbolTable = loadSymbolTable();
    }
    return mySymbolTable;
  }

  public String resolve(int id) throws CacheCorruptedException {
    return getSymbolTable().getSymbol(id);
  }

  public void registerSuperListChange(int qName) {
    myClassesWithSuperlistChanged.add(qName);
  }

  public int[] getClassesWithSuperlistChanged() {
    return myClassesWithSuperlistChanged.toArray();
  }

  /*
  public int[] getPreviouslyRemoteClasses() {
    return myPreviouslyRemoteClasses.toArray();
  }
  */
  public boolean wasRemote(int qName) {
    return myPreviouslyRemoteClasses.contains(qName);
  }

  private SymbolTable loadSymbolTable() throws CacheCorruptedException {
    SymbolTable symbolTable = null;
    File symbolTableFile = new File(mySymbolTableFilePath);
    try {
      try {
        final byte[] buf = FileUtil.loadFileBytes(symbolTableFile);
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(buf));
        try {
          symbolTable = new SymbolTable(stream);
          if ((symbolTable.getVersion() != CompilerConfiguration.DEPENDENCY_FORMAT_VERSION) || symbolTable.isFull()) {
            throw new CacheCorruptedException(CompilerBundle.message("error.caches.old.format"));
          }
        }
        finally {
          stream.close();
        }
      }
      catch (FileNotFoundException e) {
        symbolTable = new SymbolTable();
      }
    }
    catch (IOException e) {
      LOG.info(e);
      throw new CacheCorruptedException(e);
    }
    return symbolTable;
  }

  private class DeclaringClassFinder implements ClassInfoProcessor {
    private int myMemberName;
    private int myMemberDescriptor;
    private int myDeclaringClass = Cache.UNKNOWN;
    private boolean myIsField;

    public DeclaringClassFinder(MemberInfo memberInfo) {
      myMemberName = memberInfo.getName();
      myMemberDescriptor = memberInfo.getDescriptor();
      myIsField = (memberInfo instanceof FieldInfo);
    }

    public int getDeclaringClassName() {
      return myDeclaringClass;
    }

    public boolean process(int classQName) throws CacheCorruptedException {
      final int classDeclarationId = getCache().getClassDeclarationId(classQName);
      if (myIsField) {
        final int fieldId = CacheUtils.findField(getCache(), classDeclarationId, myMemberName, myMemberDescriptor);
        if (fieldId != Cache.UNKNOWN) {
          myDeclaringClass = classQName;
        }
      }
      else {
        final int methodId = CacheUtils.findMethod(getCache(), classDeclarationId, myMemberName, myMemberDescriptor);
        if (methodId != Cache.UNKNOWN) {
          myDeclaringClass = classQName;
          return false;
        }
      }
      return true;
    }

  }

  private static class FieldChangeInfo {
    final FieldInfo fieldInfo;
    final boolean isAccessibilityChange;

    public FieldChangeInfo(final FieldInfo fieldId) {
      this(fieldId, false);
    }

    public FieldChangeInfo(final FieldInfo fieldInfo, final boolean accessibilityChange) {
      this.fieldInfo = fieldInfo;
      isAccessibilityChange = accessibilityChange;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final FieldChangeInfo fieldChangeInfo = (FieldChangeInfo)o;

      if (isAccessibilityChange != fieldChangeInfo.isAccessibilityChange) return false;
      if (!fieldInfo.equals(fieldChangeInfo.fieldInfo)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = fieldInfo.hashCode();
      result = 29 * result + (isAccessibilityChange ? 1 : 0);
      return result;
    }
  }
}
