// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.PathStringDescriptor;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Mappings {
  private final static Logger LOG = Logger.getInstance(Mappings.class);
  public static final String PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY = "compiler.process.constants.non.incremental";
  private boolean myProcessConstantsIncrementally = !Boolean.valueOf(System.getProperty(PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, "false"));
  private static final boolean USE_NATURAL_INT_MULTIMAP_IMPLEMENTATION = Boolean.valueOf(System.getProperty("jps.mappings.natural.int.multimap.impl", "true"));

  private final static String CLASS_TO_SUBCLASSES = "classToSubclasses.tab";
  private final static String CLASS_TO_CLASS = "classToClass.tab";
  private final static String SHORT_NAMES = "shortNames.tab";
  private final static String SOURCE_TO_CLASS = "sourceToClass.tab";
  private final static String CLASS_TO_SOURCE = "classToSource.tab";
  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;
  private static final String IMPORT_WILDCARD_SUFFIX = ".*";

  private final boolean myIsDelta;
  private boolean myIsDifferentiated = false;
  private boolean myIsRebuild = false;

  private final IntSet myChangedClasses;
  private final Set<File> myChangedFiles;
  private final Set<Pair<ClassFileRepr, File>> myDeletedClasses;
  private final Set<ClassRepr> myAddedClasses;
  private final Object myLock;
  private final File myRootDir;

  private DependencyContext myContext;
  private final int myInitName;
  private final int myEmptyName;
  private final int myObjectClassName;
  private LoggerWrapper<Integer> myDebugS;

  private IntIntMultiMaplet myClassToSubclasses;

  /**
  key: the name of a class who is used;
  values: class names that use the class registered as the key
  */
  private IntIntMultiMaplet myClassToClassDependency;
  private ObjectObjectMultiMaplet<String, ClassFileRepr> myRelativeSourceFilePathToClasses;
  private IntObjectMultiMaplet<String> myClassToRelativeSourceFilePath;
  /**
   * [short className] -> list of FQ names
   */
  private IntIntMultiMaplet myShortClassNameIndex;

  private IntIntTransientMultiMaplet myRemovedSuperClasses;
  private IntIntTransientMultiMaplet myAddedSuperClasses;

  @Nullable
  private Collection<String> myRemovedFiles;

  public PathRelativizerService getRelativizer() {
    return myRelativizer;
  }

  private final PathRelativizerService myRelativizer;

  private Mappings(final Mappings base) throws IOException {
    myLock = base.myLock;
    myIsDelta = true;
    myChangedClasses = new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myChangedFiles = FileCollectionFactory.createCanonicalFileSet();
    myDeletedClasses = new HashSet<>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myAddedClasses = new HashSet<>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myRootDir = new File(FileUtil.toSystemIndependentName(base.myRootDir.getAbsolutePath()) + File.separatorChar + "myDelta");
    myContext = base.myContext;
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
    myObjectClassName = myContext.get("java/lang/Object");
    myDebugS = base.myDebugS;
    myRelativizer = base.myRelativizer;
    createImplementation();
  }

  public Mappings(final File rootDir, PathRelativizerService relativizer) throws IOException {
    myLock = new Object();
    myIsDelta = false;
    myChangedClasses = null;
    myChangedFiles = null;
    myDeletedClasses = null;
    myAddedClasses = null;
    myRootDir = rootDir;
    myRelativizer = relativizer;
    createImplementation();
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
    myObjectClassName = myContext.get("java/lang/Object");
  }

  private void createImplementation() throws IOException {
    try {
      if (!myIsDelta) {
        myContext = new DependencyContext(myRootDir, myRelativizer);
        myDebugS = myContext.getLogger(LOG);
      }

      myRemovedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;
      myAddedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;

      final Supplier<Collection<String>> fileCollectionFactory = CollectionFactory::createFilePathSet; // todo: do we really need set and not a list here?
      if (myIsDelta) {
        myClassToSubclasses = new IntIntTransientMultiMaplet();
        myClassToClassDependency = new IntIntTransientMultiMaplet();
        myShortClassNameIndex = null;
        myRelativeSourceFilePathToClasses = new ObjectObjectTransientMultiMaplet<>(
          FileCollectionFactory.FILE_PATH_HASH_STRATEGY, () -> new HashSet<>(5, DEFAULT_SET_LOAD_FACTOR)
        );
        myClassToRelativeSourceFilePath = new IntObjectTransientMultiMaplet<>(fileCollectionFactory);
      }
      else {
        myClassToSubclasses = new IntIntPersistentMultiMaplet(
          DependencyContext.getTableFile(myRootDir, CLASS_TO_SUBCLASSES), EnumeratorIntegerDescriptor.INSTANCE
        );
        if (USE_NATURAL_INT_MULTIMAP_IMPLEMENTATION) {
          myClassToClassDependency = new NaturalIntIntPersistentMultiMaplet(
            DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS), EnumeratorIntegerDescriptor.INSTANCE
          );
        }
        else {
          myClassToClassDependency = new IntIntPersistentMultiMaplet(
            DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS), EnumeratorIntegerDescriptor.INSTANCE
          );
        }
        myShortClassNameIndex = new IntIntPersistentMultiMaplet(
          DependencyContext.getTableFile(myRootDir, SHORT_NAMES), EnumeratorIntegerDescriptor.INSTANCE
        );
        myRelativeSourceFilePathToClasses = new ObjectObjectPersistentMultiMaplet<String, ClassFileRepr>(
          DependencyContext.getTableFile(myRootDir, SOURCE_TO_CLASS), PathStringDescriptor.INSTANCE, new ClassFileReprExternalizer(myContext),
          () -> new HashSet<>(5, DEFAULT_SET_LOAD_FACTOR)
        ) {
          @NotNull
          @Override
          protected String debugString(String path) {
            // on case-insensitive file systems save paths in normalized (lowercase) format in order to make tests run deterministically
            return SystemInfo.isFileSystemCaseSensitive ? path : path.toLowerCase(Locale.US);
          }
        };
        myClassToRelativeSourceFilePath = new IntObjectPersistentMultiMaplet<>(
          DependencyContext.getTableFile(myRootDir, CLASS_TO_SOURCE), EnumeratorIntegerDescriptor.INSTANCE, PathStringDescriptor.INSTANCE, fileCollectionFactory
        );
      }
    }
    catch (Throwable e) {
      try {
        // ensure already initialized maps are properly closed
        close();
      }
      catch (Throwable ignored) {
      }
      throw e;
    }
  }

  public void setProcessConstantsIncrementally(boolean processInc) {
    myProcessConstantsIncrementally = processInc;
  }

  public String valueOf(final int name) {
    return myContext.getValue(name);
  }

  public int getName(final String string) {
    return myContext.get(string);
  }

  public Mappings createDelta() {
    synchronized (myLock) {
      try {
        return new Mappings(this);
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }
  }

  private void compensateRemovedContent(final @NotNull Collection<? extends File> compiled, final @NotNull Collection<? extends File> compiledWithErrors) {
    for (final File file : compiled) {
      if (!compiledWithErrors.contains(file)) {
        String relative = toRelative(file);
        if (!myRelativeSourceFilePathToClasses.containsKey(relative)) {
          myRelativeSourceFilePathToClasses.put(relative, new HashSet<>());
        }
      }
    }
  }

  @Nullable
  private ClassRepr getClassReprByName(final @Nullable File source, final int qName) {
    final ClassFileRepr reprByName = getReprByName(source, qName);
    return reprByName instanceof ClassRepr? (ClassRepr)reprByName : null;
  }

  @Nullable
  private ClassFileRepr getReprByName(@Nullable File source, int qName) {
    final Iterable<String> sources = source != null? Collections.singleton(toRelative(source)) : myClassToRelativeSourceFilePath.get(qName);
    if (sources != null) {
      for (String src : sources) {
        final Collection<ClassFileRepr> reprs = myRelativeSourceFilePathToClasses.get(src);
        if (reprs != null) {
          for (ClassFileRepr repr : reprs) {
            if (repr.name == qName) {
              return repr;
            }
          }
        }
      }
    }

    return null;
  }

  private Collection<ClassFileRepr> sourceFileToClassesGet(File unchangedSource) {
    return myRelativeSourceFilePathToClasses.get(toRelative(unchangedSource));
  }

  @NotNull
  private String toRelative(File file) {
    return myRelativizer.toRelative(file.getAbsolutePath());
  }

  @Nullable
  private Iterable<File> classToSourceFileGet(int qName) {
    Collection<String> get = myClassToRelativeSourceFilePath.get(qName);
    return get == null ? null : Iterators.map(get, s -> toFull(s));
  }

  @NotNull
  private File toFull(String relativePath) {
    return new File(myRelativizer.toFull(relativePath));
  }

  public void clean() throws IOException {
    if (myRootDir != null) {
      synchronized (myLock) {
        close();
        FileUtil.delete(myRootDir);
        createImplementation();
      }
    }
  }

  public IntIntTransientMultiMaplet getRemovedSuperClasses() {
    return myRemovedSuperClasses;
  }

  public IntIntTransientMultiMaplet getAddedSuperClasses() {
    return myAddedSuperClasses;
  }

  private final LinkedBlockingQueue<Runnable> myPostPasses = new LinkedBlockingQueue<>();

  private void runPostPasses() {
    final Set<Pair<ClassFileRepr, File>> deleted = myDeletedClasses;
    if (deleted != null) {
      for (Pair<ClassFileRepr, File> pair : deleted) {
        final int deletedClassName = pair.first.name;
        final Iterable<File> sources = classToSourceFileGet(deletedClassName);
        if (sources == null || Iterators.isEmpty(sources)) { // if really deleted and not e.g. moved
          myChangedClasses.remove(deletedClassName);
        }
      }
    }
    for (Runnable pass = myPostPasses.poll(); pass != null; pass = myPostPasses.poll()) {
      pass.run();
    }
  }

  private static final ClassRepr MOCK_CLASS = null;
  private static final MethodRepr MOCK_METHOD = null;

  private final class Util {
    @Nullable
    private final Mappings myMappings;

    private Util() {
      myMappings = null;
    }

    private Util(@NotNull Mappings mappings) {
      myMappings = mappings;
    }

    IntSet appendDependents(final ClassFileRepr c, final IntSet result) {
      return appendDependents(c.name, result);
    }

    @Nullable
    IntSet appendDependents(int className, IntSet result) {
      final IntSet depClasses = myClassToClassDependency.get(className);
      if (depClasses != null) {
        result.addAll(depClasses);
      }
      return depClasses;
    }

    private IntSet propagateMemberAccessRec(final IntSet acc, final boolean isField, final boolean root, final Predicate<ProtoMember> isSame, final int reflcass) {
      if (acc.contains(reflcass)) {
        return acc; // SOE prevention
      }
      final ClassRepr repr = classReprByName(reflcass);
      if (repr != null) {
        if (!root) {
          final Set<? extends ProtoMember> members = isField ? repr.getFields() : repr.getMethods();

          for (ProtoMember m : members) {
            if (isSame.test(m)) {
              return acc;
            }
          }

          acc.add(reflcass);
        }

        final IntSet subclasses = myClassToSubclasses.get(reflcass);

        if (subclasses != null) {
          final IntIterator subclassIterator = subclasses.iterator();
          while (subclassIterator.hasNext()) {
            propagateMemberAccessRec(acc, isField, false, isSame, subclassIterator.nextInt());
          }
        }
      }
      return acc;
    }

    IntSet propagateMemberAccess(final boolean isField, final Predicate<ProtoMember> isSame, final int className) {
      return propagateMemberAccessRec(new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR), isField, true, isSame, className);
    }

    IntSet propagateFieldAccess(final int name, final int className) {
      return propagateMemberAccess(true, member -> member.name == name, className);
    }

    IntSet propagateMethodAccess(final MethodRepr m, final int className) {
      return propagateMemberAccess(false, member -> m.equals(member), className);
    }

    Predicate<MethodRepr> lessSpecific(final MethodRepr than) {
      return m -> {
        if (m.name == myInitName || m.name != than.name || m.myArgumentTypes.length != than.myArgumentTypes.length) {
          return false;
        }

        for (int i = 0; i < than.myArgumentTypes.length; i++) {
          final Boolean subtypeOf = isSubtypeOf(than.myArgumentTypes[i], m.myArgumentTypes[i]);
          if (subtypeOf != null && !subtypeOf) {
            return false;
          }
        }

        return true;
      };
    }

    private void addOverridingMethods(final MethodRepr m, final ClassRepr fromClass, final Predicate<MethodRepr> predicate, final Collection<? super Pair<MethodRepr, ClassRepr>> container, IntSet visitedClasses) {
      if (m.name == myInitName) {
        return; // overriding is not defined for constructors
      }
      final IntSet subClasses = myClassToSubclasses.get(fromClass.name);
      if (subClasses == null) {
        return;
      }
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
      }
      if (!visitedClasses.add(fromClass.name)) {
        return;
      }
      final IntSet _visitedClasses = visitedClasses;
      subClasses.forEach(subClassName -> {
        final ClassRepr r = classReprByName(subClassName);

        if (r != null) {
          boolean cont = true;
          final Collection<MethodRepr> methods = r.findMethods(predicate);
          for (MethodRepr mm : methods) {
            if (isVisibleIn(fromClass, m, r)) {
              container.add(Pair.create(mm, r));
              cont = false;
            }
          }
          if (cont) {
            addOverridingMethods(m, r, predicate, container, _visitedClasses);
          }
        }
      });
    }

    private Collection<Pair<MethodRepr, ClassRepr>> findAllMethodsBySpecificity(final MethodRepr m, final ClassRepr c) {
      final Predicate<MethodRepr> predicate = lessSpecific(m);
      final Collection<Pair<MethodRepr, ClassRepr>> result = new HashSet<>();
      addOverridenMethods(c, predicate, result, null);
      addOverridingMethods(m, c, predicate, result, null);
      return result;
    }

    private Collection<Pair<MethodRepr, ClassRepr>> findOverriddenMethods(final MethodRepr m, final ClassRepr c) {
      if (m.name == myInitName) {
        return Collections.emptySet(); // overriding is not defined for constructors
      }
      final Collection<Pair<MethodRepr, ClassRepr>> result = new HashSet<>();
      addOverridenMethods(c, MethodRepr.equalByJavaRules(m), result, null);
      return result;
    }

    private boolean hasOverriddenMethods(final ClassRepr fromClass, final Predicate<MethodRepr> predicate, IntSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (TypeRepr.ClassType superName : fromClass.getSuperTypes()) {
        if (!visitedClasses.add(superName.className) || superName.className == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = classReprByName(superName.className);
        if (superClass != null) {
          for (MethodRepr mm : superClass.findMethods(predicate)) {
            if (isVisibleIn(superClass, mm, fromClass)) {
              return true;
            }
          }
          if (hasOverriddenMethods(superClass, predicate, visitedClasses)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean extendsLibraryClass(final ClassRepr fromClass, IntSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (TypeRepr.ClassType superName : fromClass.getSuperTypes()) {
        if (!visitedClasses.add(superName.className) || superName.className == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = classReprByName(superName.className);
        if (superClass == null || extendsLibraryClass(superClass, visitedClasses)) {
          return true;
        }
      }
      return false;
    }

    private void addOverridenMethods(final ClassRepr fromClass, final Predicate<MethodRepr> predicate, final Collection<? super Pair<MethodRepr, ClassRepr>> container, IntSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (TypeRepr.ClassType superName : fromClass.getSuperTypes()) {
        if (!visitedClasses.add(superName.className) || superName.className == myObjectClassName) {
          continue;  // prevent SOE
        }
        final ClassRepr superClass = classReprByName(superName.className);
        if (superClass != null) {
          boolean cont = true;
          final Collection<MethodRepr> methods = superClass.findMethods(predicate);
          for (MethodRepr mm : methods) {
            if (isVisibleIn(superClass, mm, fromClass)) {
              container.add(Pair.create(mm, superClass));
              cont = false;
            }
          }
          if (cont) {
            addOverridenMethods(superClass, predicate, container, visitedClasses);
          }
        }
        else {
          container.add(Pair.create(MOCK_METHOD, MOCK_CLASS));
        }
      }
    }

    void addOverriddenFields(final FieldRepr f, final ClassRepr fromClass, final Collection<? super Pair<FieldRepr, ClassRepr>> container, IntSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (TypeRepr.ClassType supername : fromClass.getSuperTypes()) {
        if (!visitedClasses.add(supername.className) || supername.className == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = classReprByName(supername.className);
        if (superClass != null) {
          final FieldRepr ff = superClass.findField(f.name);
          if (ff != null && isVisibleIn(superClass, ff, fromClass)) {
            container.add(Pair.create(ff, superClass));
          }
          else{
            addOverriddenFields(f, superClass, container, visitedClasses);
          }
        }
      }
    }

    boolean hasOverriddenFields(final FieldRepr f, final ClassRepr fromClass, IntSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new IntOpenHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (TypeRepr.ClassType supername : fromClass.getSuperTypes()) {
        if (!visitedClasses.add(supername.className) || supername.className == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = classReprByName(supername.className);
        if (superClass != null) {
          final FieldRepr ff = superClass.findField(f.name);
          if (ff != null && isVisibleIn(superClass, ff, fromClass)) {
            return true;
          }
          final boolean found = hasOverriddenFields(f, superClass, visitedClasses);
          if (found) {
            return true;
          }
        }
      }
      return false;
    }

    // test if a ClassRepr is a SAM interface
    boolean isLambdaTarget(int name) {
      final ClassRepr cls = classReprByName(name);
      if (cls == null || !cls.isInterface()) {
        return false;
      }
      int amFound = 0;
      for (MethodRepr method : allMethodsRecursively(cls)) {
        if (method.isAbstract() && ++amFound > 1) {
          return false;
        }
      }
      return amFound == 1;
    }

    private Iterable<MethodRepr> allMethodsRecursively(ClassRepr cls) {
      return Iterators.flat(collectRecursively(cls, c-> c.getMethods()));
    }

    private Iterable<OverloadDescriptor> findAllOverloads(final ClassRepr cls, Function<MethodRepr, Integer> correspondenceFinder) {
      Function<ClassRepr, Iterable<OverloadDescriptor>> converter = c -> c == null? Collections.emptyList() : Iterators.filter(Iterators.map(c.getMethods(), m -> {
        Integer accessScope = correspondenceFinder.apply(m);
        return accessScope != null? new OverloadDescriptor(accessScope, m, c) : null;
      }), Objects::nonNull);

      return Iterators.flat(Iterators.flat(
        collectRecursively(cls, converter),
        Iterators.map(getAllSubclasses(cls.name), subName -> converter.apply(subName != cls.name? classReprByName(subName) : null))
      ));
    }

    private <T> Iterable<T> collectRecursively(ClassRepr cls, Function<ClassRepr, T> mapper) {
      return Iterators.flat(Iterators.asIterable(mapper.apply(cls)), Iterators.flat(Iterators.map(cls.getSuperTypes(), st -> {
        final ClassRepr cr = classReprByName(st.className);
        return cr != null ? collectRecursively(cr, mapper) : Collections.emptyList();
      })));
    }

    @Nullable
    ClassRepr classReprByName(final int name) {
      final ClassFileRepr r = reprByName(name);
      return r instanceof ClassRepr? (ClassRepr)r : null;
    }

    @Nullable
    ModuleRepr moduleReprByName(final int name) {
      final ClassFileRepr r = reprByName(name);
      return r instanceof ModuleRepr? (ModuleRepr)r : null;
    }

    @Nullable
    ClassFileRepr reprByName(final int name) {
      if (myMappings != null) {
        final ClassFileRepr r = myMappings.getReprByName(null, name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(null, name);
    }

    @Nullable
    private Boolean isInheritorOf(final int who, final int whom, IntSet visitedClasses) {
      if (who == whom) {
        return Boolean.TRUE;
      }

      final ClassRepr repr = classReprByName(who);

      if (repr != null) {
        if (visitedClasses == null) {
          visitedClasses = new IntOpenHashSet();
          visitedClasses.add(who);
        }
        for (TypeRepr.ClassType s : repr.getSuperTypes()) {
          if (!visitedClasses.add(s.className)) {
            continue;
          }
          if (Boolean.TRUE.equals(isInheritorOf(s.className, whom, visitedClasses))) {
            return Boolean.TRUE;
          }
        }
      }

      return null;
    }

    @Nullable
    Boolean isSubtypeOf(final TypeRepr.AbstractType who, final TypeRepr.AbstractType whom) {
      if (who.equals(whom)) {
        return Boolean.TRUE;
      }

      if (who instanceof TypeRepr.PrimitiveType || whom instanceof TypeRepr.PrimitiveType) {
        return Boolean.FALSE;
      }

      if (who instanceof TypeRepr.ArrayType) {
        if (whom instanceof TypeRepr.ArrayType) {
          return isSubtypeOf(((TypeRepr.ArrayType)who).elementType, ((TypeRepr.ArrayType)whom).elementType);
        }

        final String descr = whom.getDescr(myContext);

        if (descr.equals("Ljava/lang/Cloneable") || descr.equals("Ljava/lang/Object") || descr.equals("Ljava/io/Serializable")) {
          return Boolean.TRUE;
        }

        return Boolean.FALSE;
      }

      if (whom instanceof TypeRepr.ClassType) {
        return isInheritorOf(((TypeRepr.ClassType)who).className, ((TypeRepr.ClassType)whom).className, null);
      }

      return Boolean.FALSE;
    }

    boolean isMethodVisible(final ClassRepr classRepr, final MethodRepr m) {
      return classRepr.findMethods(MethodRepr.equalByJavaRules(m)).size() > 0 || hasOverriddenMethods(classRepr, MethodRepr.equalByJavaRules(m), null);
    }

    boolean isFieldVisible(final int className, final FieldRepr field) {
      final ClassRepr r = classReprByName(className);
      if (r == null || r.getFields().contains(field)) {
        return true;
      }
      return hasOverriddenFields(field, r, null);
    }

    void collectSupersRecursively(final int className, @NotNull final IntSet container) {
      final ClassRepr classRepr = classReprByName(className);
      if (classRepr != null) {
        final Iterable<TypeRepr.ClassType> supers = classRepr.getSuperTypes();
        boolean added = false;
        for (TypeRepr.ClassType aSuper : supers) {
          added |= container.add(aSuper.className);
        }
        if (added) {
          for (TypeRepr.ClassType aSuper : supers) {
            collectSupersRecursively(aSuper.className, container);
          }
        }
      }
    }

    void affectSubclasses(final int className,
                          final Collection<? super File> affectedFiles,
                          final Collection<? super UsageRepr.Usage> affectedUsages,
                          final IntSet dependants,
                          final boolean usages,
                          final Collection<? extends File> alreadyCompiledFiles,
                          IntSet visitedClasses) {
      debug("Affecting subclasses of class: ", className);

      final Iterable<File> allSources = classToSourceFileGet(className);
      if (allSources == null || Iterators.isEmpty(allSources)) {
        debug("No source file detected for class ", className);
        debug("End of affectSubclasses");
        return;
      }

      for (File fName : allSources) {
        debug("Source file name: ", fName);
        if (!alreadyCompiledFiles.contains(fName)) {
          affectedFiles.add(fName);
        }
      }

      if (usages) {
        debug("Class usages affection requested");

        final ClassRepr classRepr = classReprByName(className);
        if (classRepr != null) {
          debug("Added class usage for ", classRepr.name);
          affectedUsages.add(classRepr.createUsage());
        }
      }

      appendDependents(className, dependants);

      final IntSet directSubclasses = myClassToSubclasses.get(className);
      if (directSubclasses != null) {
        if (visitedClasses == null) {
          visitedClasses = new IntOpenHashSet();
          visitedClasses.add(className);
        }
        final IntSet _visitedClasses = visitedClasses;
        directSubclasses.forEach(subClass -> {
          if (_visitedClasses.add(subClass)) {
            affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages, alreadyCompiledFiles, _visitedClasses);
          }
        });
      }
    }

    void affectFieldUsages(final FieldRepr field, final IntSet classes, final UsageRepr.Usage rootUsage, final Set<? super UsageRepr.Usage> affectedUsages, final IntSet dependents) {
      affectedUsages.add(rootUsage);

      classes.forEach(p -> {
        appendDependents(p, dependents);
        debug("Affect field usage referenced of class ", p);
        affectedUsages.add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(myContext, p) : field.createUsage(myContext, p));
      });
    }

    void affectStaticMemberImportUsages(final int memberName, int ownerName, final IntSet classes, final Set<? super UsageRepr.Usage> affectedUsages, final IntSet dependents) {
      debug("Affect static member import usage referenced of class ", ownerName);
      affectedUsages.add(UsageRepr.createImportStaticMemberUsage(myContext, memberName, ownerName));

      classes.forEach(cls -> {
        appendDependents(cls, dependents);
        debug("Affect static member import usage referenced of class ", cls);
        affectedUsages.add(UsageRepr.createImportStaticMemberUsage(myContext, memberName, cls));
      });
    }

    void affectStaticMemberOnDemandUsages(int ownerClass, final IntSet classes, final Set<? super UsageRepr.Usage> affectedUsages, final IntSet dependents) {
      debug("Affect static member on-demand import usage referenced of class ", ownerClass);
      affectedUsages.add(UsageRepr.createImportStaticOnDemandUsage(myContext, ownerClass));

      classes.forEach(cls -> {
        appendDependents(cls, dependents);
        debug("Affect static member on-demand import usage referenced of class ", cls);
        affectedUsages.add(UsageRepr.createImportStaticOnDemandUsage(myContext, cls));
      });
    }

    void affectMethodUsagesThrowing(ClassRepr aClass, TypeRepr.ClassType exceptionClass, final Set<? super UsageRepr.Usage> affectedUsages, final IntSet dependents) {
      boolean shouldAffect = false;
      for (MethodRepr method : aClass.getMethods()) {
        if (method.myExceptions.contains(exceptionClass)) {
          shouldAffect = true;
          affectedUsages.add(method.createUsage(myContext, aClass.name));
        }
      }
      if (shouldAffect) {
        if (myDebugS.isDebugEnabled()) {
          debug("Affecting usages of methods throwing "+ myContext.getValue(exceptionClass.className) + " exception; class ", aClass.name);
        }
        appendDependents(aClass, dependents);
      }
    }

    void affectMethodUsages(final MethodRepr method, final IntSet subclasses, final UsageRepr.Usage rootUsage, final Set<? super UsageRepr.Usage> affectedUsages, final IntSet dependents) {
      affectedUsages.add(rootUsage);
      if (subclasses != null) {
        subclasses.forEach(p -> {
          appendDependents(p, dependents);

          debug("Affect method usage referenced of class ", p);

          final UsageRepr.Usage usage =
            rootUsage instanceof UsageRepr.MetaMethodUsage ? method.createMetaUsage(myContext, p) : method.createUsage(myContext, p);
          affectedUsages.add(usage);
        });
      }
    }

    void affectModule(ModuleRepr m, final Collection<? super File> affectedFiles) {
      Iterable<File> depFiles = myMappings != null ? myMappings.classToSourceFileGet(m.name) : null;
      if (depFiles == null) {
        depFiles = classToSourceFileGet(m.name);
      }
      if (depFiles != null) {
        debug("Affecting module ", m.name);
        ContainerUtil.addAll(affectedFiles, depFiles);
      }
    }

    void affectDependentModules(Differential.DiffState state, final int moduleName, @Nullable UsageConstraint constraint, boolean checkTransitive) {
      new Object() {
        final IntSet visited = new IntOpenHashSet();

        void perform(final int modName) {
          final IntSet depNames = myClassToClassDependency.get(modName);
          if (depNames != null && !depNames.isEmpty()) {
            final IntSet next = new IntOpenHashSet();
            final UsageRepr.Usage moduleUsage = UsageRepr.createModuleUsage(myContext, modName);
            state.myAffectedUsages.add(moduleUsage);
            final UsageConstraint prevConstraint = state.myUsageConstraints.put(moduleUsage, constraint == null? UsageConstraint.ANY : constraint);
            if (prevConstraint != null) {
              state.myUsageConstraints.put(moduleUsage, prevConstraint.or(constraint));
            }
            depNames.forEach(depName -> {
              if (visited.add(depName)) {
                final ClassFileRepr depRepr = reprByName(depName);
                if (depRepr instanceof ModuleRepr) {
                  state.myDependants.add(depName);
                  if (checkTransitive && ((ModuleRepr)depRepr).requiresTransitevely(modName)) {
                    next.add(depName);
                  }
                }
              }
            });
            next.forEach(this::perform);
          }
        }
      }.perform(moduleName);
    }

    void affectLambdaInstantiations(Differential.DiffState state, final int className) {
      getAllSubclasses(className).forEach(name -> {
        if (isLambdaTarget(name)) {
          debug("The interface could be not a SAM interface anymore or lambda target method name has changed => affecting lambda instantiations for ", name);
          if (state.myAffectedUsages.add(UsageRepr.createClassNewUsage(myContext, name))) {
            appendDependents(name, state.myDependants);
          }
        }
      });
    }

    public class FileFilterConstraint implements UsageConstraint {
      @NotNull
      private final DependentFilesFilter myFilter;

      public FileFilterConstraint(@NotNull DependentFilesFilter filter) {
        myFilter = filter;
      }

      @Override
      public boolean checkResidence(int residence) {
        final Iterable<File> fNames = classToSourceFileGet(residence);
        if (fNames == null || Iterators.isEmpty(fNames)) {
          return true;
        }
        for (File fName : fNames) {
          if (myFilter.accept(fName)) {
            return true;
          }
        }
        return false;
      }
    }

    public class PackageConstraint implements UsageConstraint {
      public final String packageName;

      public PackageConstraint(final String packageName) {
        this.packageName = packageName;
      }

      @Override
      public boolean checkResidence(final int residence) {
        final String className = myContext.getValue(residence);
        return className == null || !ClassRepr.getPackageName(className).equals(packageName);
      }
    }

    public class InheritanceConstraint extends PackageConstraint {
      public final int rootClass;

      public InheritanceConstraint(ClassRepr rootClass) {
        super(rootClass.getPackageName());
        this.rootClass = rootClass.name;
      }

      @Override
      public boolean checkResidence(final int residence) {
        final Boolean inheritorOf = isInheritorOf(residence, rootClass, null);
        return (inheritorOf == null || !inheritorOf) && super.checkResidence(residence);
      }
    }
  }

  void affectAll(final int className,
                 @NotNull final File sourceFile,
                 final Collection<? super File> affectedFiles,
                 final Collection<? extends File> alreadyCompiledFiles,
                 @Nullable final DependentFilesFilter filter) {
    final IntSet dependants = myClassToClassDependency.get(className);
    if (dependants != null) {
      dependants.forEach(depClass -> {
        final Iterable<File> allSources = classToSourceFileGet(depClass);
        if (allSources == null || Iterators.isEmpty(allSources)) {
          return;
        }

        boolean shouldAffect = false;
        for (File depFile : allSources) {
          if (FileUtil.filesEqual(depFile, sourceFile)) {
            continue;  // skipping self-dependencies
          }
          if (!alreadyCompiledFiles.contains(depFile) && (filter == null || filter.accept(depFile))) {
            // if at least one of the source files associated with the class is affected, all other associated sources should be affected as well
            shouldAffect = true;
            break;
          }
        }

        if (shouldAffect) {
          for (File depFile : allSources) {
            if (!FileUtil.filesEqual(depFile, sourceFile)) {
              affectedFiles.add(depFile);
            }
          }
        }
      });
    }
  }

  private static boolean isVisibleIn(final ClassRepr c, final ProtoMember m, final ClassRepr scope) {
    final boolean privacy = m.isPrivate() && c.name != scope.name;
    final boolean packageLocality = m.isPackageLocal() && !c.getPackageName().equals(scope.getPackageName());
    return !privacy && !packageLocality;
  }

  private boolean isEmpty(final int s) {
    return s == myEmptyName;
  }

  @NotNull
  private IntSet getAllSubclasses(final int root) {
    return addAllSubclasses(root, new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR));
  }

  private IntSet addAllSubclasses(final int root, final IntSet acc) {
    if (acc.add(root)) {
      final IntSet directSubclasses = myClassToSubclasses.get(root);
      if (directSubclasses != null) {
        directSubclasses.forEach(s -> {
          addAllSubclasses(s, acc);
        });
      }
    }
    return acc;
  }

  private boolean incrementalDecision(final int owner,
                                      final Proto member,
                                      final Collection<? super File> affectedFiles,
                                      final Collection<? extends File> currentlyCompiled,
                                      @Nullable final DependentFilesFilter filter) {
    final boolean isField = member instanceof FieldRepr;
    final Util self = new Util();

    // Public branch --- hopeless
    if (member.isPublic()) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    final Set<File> toRecompile = FileCollectionFactory.createCanonicalFileSet();

    // Protected branch
    if (member.isProtected()) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);

      final IntSet propagated = self.propagateFieldAccess(isField ? member.name : myEmptyName, owner);
      propagated.forEach(className -> {
        final Iterable<File> fileNames = classToSourceFileGet(className);
        if (fileNames != null) {
          if (myDebugS.isDebugEnabled()) {
            for (File fileName : fileNames) {
              debug("Adding ", fileName);
            }
          }
          ContainerUtil.addAll(toRecompile, fileNames);
        }
      });
    }

    final String cName = myContext.getValue(isField ? owner : member.name);
    if (cName != null) {
      final String packageName = ClassRepr.getPackageName(cName);

      debug("Softening non-incremental decision: adding all package classes for a recompilation");
      debug("Package name: ", packageName);

      // Package-local branch
      myClassToRelativeSourceFilePath.forEachEntry((relFilePaths, value) -> {
        final String clsName = myContext.getValue(value);
        if (clsName != null && ClassRepr.getPackageName(clsName).equals(packageName)) {
          for (String rel : relFilePaths) {
            File file = toFull(rel);
            if (filter == null || filter.accept(file)) {
              debug("Adding: ", rel);
              toRecompile.add(file);
            }
          }
        }
      });
    }

    // filtering already compiled and non-existing paths
    toRecompile.removeAll(currentlyCompiled);
    for (Iterator<File> it = toRecompile.iterator(); it.hasNext(); ) {
      final File file = it.next();
      if (!file.exists()) {
        it.remove();
      }
    }

    affectedFiles.addAll(toRecompile);

    return true;
  }

  public interface DependentFilesFilter {
    boolean accept(File file);

    boolean belongsToCurrentTargetChunk(File file);
  }

  private final class Differential {
    private static final int INLINABLE_FIELD_MODIFIERS_MASK = Opcodes.ACC_FINAL;

    final Mappings myDelta;
    final Collection<? extends File> myFilesToCompile;
    final Collection<? extends File> myCompiledFiles;
    final Collection<? extends File> myCompiledWithErrors;
    final Collection<? super File> myAffectedFiles;
    @Nullable
    final DependentFilesFilter myFilter;

    final Util myFuture;
    final Util myPresent;

    final boolean myEasyMode; // true means: no need to search for affected files, only preprocess data for integrate

    private final Iterable<AnnotationsChangeTracker> myAnnotationChangeTracker =
      JpsServiceManager.getInstance().getExtensions(AnnotationsChangeTracker.class);

    private class FileClasses {
      final File myFileName;
      final Set<ClassRepr> myFileClasses = new HashSet<>();
      final Set<ModuleRepr> myFileModules = new HashSet<>();

      FileClasses(File fileName, Collection<ClassFileRepr> fileContent) {
        myFileName = fileName;
        for (ClassFileRepr repr : fileContent) {
          if (repr instanceof ClassRepr) {
            myFileClasses.add((ClassRepr)repr);
          }
          else {
            myFileModules.add((ModuleRepr)repr);
          }
        }
      }
    }

    private final class DiffState {
      final public IntSet myDependants = new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

      final public Set<UsageRepr.Usage> myAffectedUsages = new HashSet<>();
      final public Set<UsageRepr.AnnotationUsage> myAnnotationQuery = new HashSet<>();
      final public Map<UsageRepr.Usage, UsageConstraint> myUsageConstraints = new HashMap<>();

      final Difference.Specifier<ClassRepr, ClassRepr.Diff> myClassDiff;
      final Difference.Specifier<ModuleRepr, ModuleRepr.Diff> myModulesDiff;

      DiffState(Difference.Specifier<ClassRepr, ClassRepr.Diff> classDiff, Difference.Specifier<ModuleRepr, ModuleRepr.Diff> modulesDiff) {
        myClassDiff = classDiff;
        myModulesDiff = modulesDiff;
      }
    }

    private Differential(final Mappings delta) {
      this.myDelta = delta;
      this.myFilesToCompile = null;
      this.myCompiledFiles = null;
      this.myCompiledWithErrors = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      myFuture = null;
      myPresent = null;
      myEasyMode = true;
      delta.myIsRebuild = true;
    }

    private Differential(final Mappings delta, final Collection<String> removed, final Collection<? extends File> filesToCompile) {
      delta.myRemovedFiles = removed;
      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = null;
      this.myCompiledWithErrors = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      myFuture = new Util(delta);
      myPresent = new Util();
      myEasyMode = true;
    }

    private Differential(final Mappings delta,
                         final Collection<String> removed,
                         final Collection<? extends File> filesToCompile,
                         final Collection<? extends File> compiledWithErrors,
                         final Collection<? extends File> compiledFiles,
                         final Collection<? super File> affectedFiles,
                         @NotNull final DependentFilesFilter filter) {
      delta.myRemovedFiles = removed;

      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = compiledFiles;
      this.myCompiledWithErrors = compiledWithErrors;
      this.myAffectedFiles = affectedFiles;
      this.myFilter = filter;

      myFuture = new Util(delta);
      myPresent = new Util();

      myEasyMode = false;
    }

    private void processDisappearedClasses() {
      if (myFilesToCompile != null) {
        myDelta.compensateRemovedContent(
          myFilesToCompile, myCompiledWithErrors != null ? myCompiledWithErrors : Collections.emptySet()
        );
      }

      if (!myEasyMode) {
        final Collection<String> removed = myDelta.myRemovedFiles;

        if (removed != null) {
          for (final String file : removed) {
            final File sourceFile = new File(file);
            final Collection<ClassFileRepr> classes = sourceFileToClassesGet(sourceFile);

            if (classes != null) {
              for (ClassFileRepr c : classes) {
                debug("Affecting usages of removed class ", c.name);
                affectAll(c.name, sourceFile, myAffectedFiles, myCompiledFiles, myFilter);
              }
            }
          }
        }
      }
    }

    private void processAddedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<MethodRepr> added = diff.methods().added();
      if (added.isEmpty()) {
        return;
      }
      debug("Processing added methods: ");
      if (it.isAnnotation()) {
        debug("Class is annotation, skipping method analysis");
        return;
      }

      assert myFuture != null;
      assert myPresent != null;
      assert myAffectedFiles != null;

      for (final MethodRepr m : added) {
        if (!m.isPrivate() && (it.isInterface() || it.isAbstract() || m.isAbstract())) {
          debug("Method: ", m.name);
          debug("Class is abstract, or is interface, or added non-private method is abstract => affecting all subclasses");
          myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
          break;
        }
      }

      if (it.isInterface()) {
        for (final MethodRepr m : added) {
          if (!m.isPrivate() && m.isAbstract()) {
            debug("Added non-private abstract method: ", m.name);
            myPresent.affectLambdaInstantiations(state, it.name);
            break;
          }
        }
      }

      for (final MethodRepr addedMethod : added) {
        debug("Method: ", addedMethod.name);

        final Supplier<IntSet> propagated = lazy(()-> myFuture.propagateMethodAccess(addedMethod, it.name));

        if (!addedMethod.isPrivate() && addedMethod.myArgumentTypes.length > 0 && !myPresent.hasOverriddenMethods(it, MethodRepr.equalByJavaRules(addedMethod), null)) {
          debug("Conservative case on overriding methods, affecting method usages");
          // do not propagate constructors access, since constructors are always concrete and not accessible via references to subclasses
          myFuture.affectMethodUsages(addedMethod, addedMethod.name == myInitName? null : propagated.get(), addedMethod.createMetaUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
        }

        if (!addedMethod.isPrivate()) {
          if (addedMethod.isStatic()) {
            myFuture.affectStaticMemberOnDemandUsages(it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
          }

          final Collection<MethodRepr> removed = diff.methods().removed();
          for (final MethodRepr lessSpecific : it.findMethods(myFuture.lessSpecific(addedMethod))) {
            if (!lessSpecific.equals(addedMethod) && !removed.contains(lessSpecific))  {
              debug("Found less specific method, affecting method usages");
              myFuture.affectMethodUsages(lessSpecific, propagated.get(), lessSpecific.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
            }
          }

          debug("Processing affected by specificity methods");
          final Collection<Pair<MethodRepr, ClassRepr>> affectedMethods = myFuture.findAllMethodsBySpecificity(addedMethod, it);
          final Predicate<MethodRepr> overrides = MethodRepr.equalByJavaRules(addedMethod);

          for (final Pair<MethodRepr, ClassRepr> pair : affectedMethods) {
            final MethodRepr method = pair.first;
            final ClassRepr methodClass = pair.second;

            if (methodClass == MOCK_CLASS) {
              continue;
            }
            final boolean isInheritor = Boolean.TRUE.equals(myPresent.isInheritorOf(methodClass.name, it.name, null));

            debug("Method: ", method.name);
            debug("Class : ", methodClass.name);

            if (overrides.test(method) && isInheritor) {
              debug("Current method overrides that found");

              final Iterable<File> files = classToSourceFileGet(methodClass.name);
              if (files != null) {
                ContainerUtil.addAll(myAffectedFiles, files);
                if (myDebugS.isDebugEnabled()) {
                  for (File file : files) {
                    debug("Affecting file ", file);
                  }
                }
              }

            }
            else {
              debug("Current method does not override that found");

              final IntSet yetPropagated = myPresent.propagateMethodAccess(method, it.name);

              if (isInheritor) {
                myPresent.appendDependents(methodClass, state.myDependants);
                myFuture.affectMethodUsages(method, yetPropagated, method.createUsage(myContext, methodClass.name), state.myAffectedUsages, state.myDependants);
              }

              debug("Affecting method usages for that found");
              myFuture.affectMethodUsages(method, yetPropagated, method.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
            }
          }

          getAllSubclasses(it.name).forEach(subClass -> {
            final ClassRepr r = myFuture.classReprByName(subClass);
            if (r == null) {
              return;
            }
            final Iterable<File> sourceFileNames = classToSourceFileGet(subClass);
            if (sourceFileNames != null && !containsAll(myCompiledFiles, sourceFileNames)) {
              final int outerClass = r.getOuterClassName();
              if (!isEmpty(outerClass)) {
                final ClassRepr outerClassRepr = myFuture.classReprByName(outerClass);
                if (outerClassRepr != null && (myFuture.isMethodVisible(outerClassRepr, addedMethod) || myFuture.extendsLibraryClass(outerClassRepr, null))) {
                  ContainerUtil.addAll(myAffectedFiles, sourceFileNames);
                  for (File sourceFileName : sourceFileNames) {
                    debug("Affecting file due to local overriding: ", sourceFileName);
                  }
                }
              }
            }
          });
        }
      }
      debug("End of added methods processing");
    }

    private void processRemovedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<MethodRepr> removed = diff.methods().removed();
      if (removed.isEmpty()) {
        return;
      }
      assert myFuture != null;
      assert myPresent != null;
      assert myAffectedFiles != null;
      assert myCompiledFiles != null;

      debug("Processing removed methods:");
      for (final MethodRepr m : removed) {
        debug("Method ", m.name);

        final Collection<Pair<MethodRepr, ClassRepr>> overriddenMethods = myFuture.findOverriddenMethods(m, it);
        final Supplier<IntSet> propagated = lazy(()-> myFuture.propagateMethodAccess(m, it.name));

        if (!m.isPrivate() && m.isStatic()) {
          debug("The method was static --- affecting static method import usages");
          myFuture.affectStaticMemberImportUsages(m.name, it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
        }

        if (overriddenMethods.size() == 0) {
          debug("No overridden methods found, affecting method usages");
          myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
        }
        else {
          boolean clear = true;

          loop:
          for (final Pair<MethodRepr, ClassRepr> overriden : overriddenMethods) {
            final MethodRepr mm = overriden.first;

            if (mm == MOCK_METHOD || !mm.myType.equals(m.myType) || !isEmpty(mm.signature) || !isEmpty(m.signature) || m.isMoreAccessibleThan(mm)) {
              clear = false;
              break loop;
            }
          }

          if (!clear) {
            debug("No clearly overridden methods found, affecting method usages");
            myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
          }
        }

        final Collection<Pair<MethodRepr, ClassRepr>> overridingMethods = new HashSet<>();

        myFuture.addOverridingMethods(m, it, MethodRepr.equalByJavaRules(m), overridingMethods, null);

        for (final Pair<MethodRepr, ClassRepr> p : overridingMethods) {
          final Iterable<File> fNames = classToSourceFileGet(p.second.name);
          if (fNames != null) {
            ContainerUtil.addAll(myAffectedFiles, fNames);
            if (myDebugS.isDebugEnabled()) {
              for (File fName : fNames) {
                debug("Affecting file by overriding: ", fName);
              }
            }
          }
        }

        if (!m.isAbstract() && !m.isStatic()) {
          propagated.get().forEach(p -> {
            if (p != it.name) {
              final ClassRepr s = myFuture.classReprByName(p);

              if (s != null) {
                final Collection<Pair<MethodRepr, ClassRepr>> overridenInS = myFuture.findOverriddenMethods(m, s);

                overridenInS.addAll(overriddenMethods);

                boolean allAbstract = true;
                boolean visited = false;

                for (final Pair<MethodRepr, ClassRepr> pp : overridenInS) {
                  final ClassRepr cc = pp.second;

                  if (cc == MOCK_CLASS) {
                    visited = true;
                    continue;
                  }

                  if (cc.name == it.name) {
                    continue;
                  }

                  visited = true;
                  allAbstract = pp.first.isAbstract() || cc.isInterface();

                  if (!allAbstract) {
                    break;
                  }
                }

                if (allAbstract && visited) {
                  final Iterable<File> sources = classToSourceFileGet(p);

                  if (sources != null && !containsAll(myCompiledFiles, sources)) {
                    ContainerUtil.addAll(myAffectedFiles, sources);
                    debug("Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass ", p);
                    for (File source : sources) {
                      debug("Affecting subclass source file ", source);
                    }
                  }
                }
              }
            }
          });
        }
      }
      debug("End of removed methods processing");
    }

    private void processChangedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<Pair<MethodRepr, MethodRepr.Diff>> changed = diff.methods().changed();
      if (changed.isEmpty()) {
        return;
      }
      debug("Processing changed methods:");

      assert myPresent != null;
      assert myFuture != null;
      assert myAffectedFiles != null;

      if (it.isInterface()) {
        for (final Pair<MethodRepr, MethodRepr.Diff> mr : changed) {
          if ((mr.second.removedModifiers() & Opcodes.ACC_ABSTRACT) != 0) {
            debug("Method became non-abstract: ", mr.first.name);
            myPresent.affectLambdaInstantiations(state, it.name);
            break;
          }
        }
      }

      final List<Pair<MethodRepr, MethodRepr.Diff>> moreAccessible = new ArrayList<>();

      for (final Pair<MethodRepr, MethodRepr.Diff> mr : changed) {
        final MethodRepr m = mr.first;
        final MethodRepr.Diff d = mr.second;
        final boolean throwsChanged = !d.exceptions().unchanged();

        debug("Method: ", m.name);

        if (d.accessExpanded()) {
          moreAccessible.add(mr);
        }
        
        if (it.isAnnotation()) {
          if (d.defaultRemoved()) {
            debug("Class is annotation, default value is removed => adding annotation query");
            IntSet l = new IntOpenHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
            l.add(m.name);
            final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)UsageRepr
              .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), l, null);
            state.myAnnotationQuery.add(annotationUsage);
          }
        }
        else if (d.base() != Difference.NONE || throwsChanged) {
          final Supplier<IntSet> propagated = lazy(()-> myFuture.propagateMethodAccess(m, it.name));

          boolean affected = false;
          boolean constrained = false;

          final Set<UsageRepr.Usage> usages = new HashSet<>();

          if (d.packageLocalOn()) {
            debug("Method became package-private, affecting method usages outside the package");
            myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), usages, state.myDependants);

            for (final UsageRepr.Usage usage : usages) {
              state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(it.getPackageName()));
            }

            state.myAffectedUsages.addAll(usages);
            affected = true;
            constrained = true;
          }

          if ((d.base() & Difference.TYPE) != 0 || (d.base() & Difference.SIGNATURE) != 0 || throwsChanged) {
            if (!affected) {
              debug("Return type, throws list or signature changed --- affecting method usages");
              myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), usages, state.myDependants);

              final List<Pair<MethodRepr, ClassRepr>> overridingMethods = new LinkedList<>();

              myFuture.addOverridingMethods(m, it, MethodRepr.equalByJavaRules(m), overridingMethods, null);

              for(final Pair<MethodRepr, ClassRepr> p : overridingMethods) {
                final ClassRepr aClass = p.getSecond();

                if (aClass != MOCK_CLASS) {
                  final Iterable<File> fileNames = classToSourceFileGet(aClass.name);
                  if (fileNames != null) {
                    ContainerUtil.addAll(myAffectedFiles, fileNames);
                  }
                }
              }

              state.myAffectedUsages.addAll(usages);
              affected = true;
            }
          }
          else if ((d.base() & Difference.ACCESS) != 0) {
            if ((d.addedModifiers() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) != 0) {

              // When synthetic or bridge flags are added, this effectively means that explicitly written in the code
              // method with the same signature and return type has been removed and a bridge method has been generated instead.
              // In some cases (e.g. using raw types) the presence of such synthetic methods in the bytecode is ignored by the compiler
              // so that the code that called such method via raw type reference might not compile anymore => to be on the safe side
              // we should recompile all places where the method was used

              if (!affected) {
                debug("Added {static | private | synthetic | bridge} specifier or removed static specifier --- affecting method usages");
                myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
                affected = true;
              }

              if ((d.addedModifiers() & Opcodes.ACC_STATIC) != 0) {
                debug("Added static specifier --- affecting subclasses");
                myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);

                if (!m.isPrivate()) {
                  debug("Added static modifier --- affecting static member on-demand import usages");
                  myFuture.affectStaticMemberOnDemandUsages(it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
                }
              }
              else if ((d.removedModifiers() & Opcodes.ACC_STATIC) != 0) {
                if (!m.isPrivate()) {
                  debug("Removed static modifier --- affecting static method import usages");
                  myFuture.affectStaticMemberImportUsages(m.name, it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
                }
              }
            }
            else {
              if ((d.addedModifiers() & (Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)) != 0) {
                debug("Added final, public or abstract specifier --- affecting subclasses");
                myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
                if (it.isInterface() && (d.addedModifiers() & Opcodes.ACC_ABSTRACT) != 0) {
                  myPresent.affectLambdaInstantiations(state, it.name);
                }
              }

              if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) != 0 && (d.removedModifiers() & Opcodes.ACC_PRIVATE) == 0) {
                if (!constrained) {
                  debug("Added public or package-private method became protected --- affect method usages with protected constraint");
                  if (!affected) {
                    myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), usages, state.myDependants);
                    state.myAffectedUsages.addAll(usages);
                    affected = true;
                  }

                  for (final UsageRepr.Usage usage : usages) {
                    state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(it));
                  }
                  constrained = true;
                }
              }
            }
          }

          if ((d.base() & Difference.ANNOTATIONS) != 0) {
            final Set<AnnotationsChangeTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationsChangeTracker.Recompile.class);
            for (AnnotationsChangeTracker extension : myAnnotationChangeTracker) {
              if (toRecompile.containsAll(AnnotationsChangeTracker.RECOMPILE_ALL)) {
                break;
              }
              final Set<AnnotationsChangeTracker.Recompile> actions = extension.methodAnnotationsChanged(myContext, (ProtoMethodEntity)m, d.annotations(), d.parameterAnnotations());
              if (actions.contains(AnnotationsChangeTracker.Recompile.USAGES)) {
                debug("Extension "+extension.getClass().getName()+" requested recompilation because of changes in annotations list --- affecting method usages");
              }
              if (actions.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
                debug("Extension "+extension.getClass().getName()+" requested recompilation because of changes in method annotations or method parameter annotations list --- affecting subclasses");
              }
              toRecompile.addAll(actions);
            }

            if (toRecompile.contains(AnnotationsChangeTracker.Recompile.USAGES)) {
              myFuture.affectMethodUsages(m, propagated.get(), m.createUsage(myContext, it.name), usages, state.myDependants);
              state.myAffectedUsages.addAll(usages);
              if (constrained) {
                // remove any constraints so that all usages of this method are recompiled
                for (UsageRepr.Usage usage : usages) {
                  state.myUsageConstraints.remove(usage);
                }
              }
            }
            if (toRecompile.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
              myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
            }

          }
        }
      }

      if (!moreAccessible.isEmpty()) {
        final Iterable<OverloadDescriptor> allOverloads = myFuture.findAllOverloads(it, mr -> {
          Integer result = null;
          for (Pair<MethodRepr, MethodRepr.Diff> pair : moreAccessible) {
            MethodRepr m = pair.first;
            MethodRepr.Diff d = pair.second;
            if (mr.name == m.name && !m.equals(mr)) {
              int newAccess = m.access & (~d.removedModifiers()) | d.addedModifiers();
              if (result == null || Difference.weakerAccess(result, newAccess)) {
                result = newAccess;
              }
            }
          }
          return result;
        });
        for (OverloadDescriptor descr : allOverloads) {

          debug("Method became more accessible --- affect usages of overloading methods: ", descr.overloadMethod.name);

          final Set<UsageRepr.Usage> overloadsUsages = new HashSet<>();
          myFuture.affectMethodUsages(
            descr.overloadMethod, myFuture.propagateMethodAccess(descr.overloadMethod, descr.overloadMethodOwner.name), descr.overloadMethod.createUsage(myContext, descr.overloadMethodOwner.name), overloadsUsages, state.myDependants
          );
          state.myAffectedUsages.addAll(overloadsUsages);
          final UsageConstraint constr = Difference.isPackageLocal(descr.accessScope)? myFuture.new PackageConstraint(it.getPackageName()).negate() :
                                         Difference.isProtected(descr.accessScope)? myFuture.new InheritanceConstraint(it).negate() : null;
          if (constr != null) {
            for (final UsageRepr.Usage usage : overloadsUsages) {
              state.myUsageConstraints.put(usage, constr);
            }
          }
        }
      }
      
      debug("End of changed methods processing");
    }

    private boolean processAddedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr classRepr) {
      final Collection<FieldRepr> added = diff.fields().added();
      if (added.isEmpty()) {
        return true;
      }
      debug("Processing added fields");

      assert myFuture != null;
      assert myPresent != null;
      assert myCompiledFiles != null;
      assert myAffectedFiles != null;

      for (final FieldRepr f : added) {
        debug("Field: ", f.name);

        if (!f.isPrivate()) {
          getAllSubclasses(classRepr.name).forEach(subClass -> {
            final ClassRepr r = myFuture.classReprByName(subClass);
            if (r != null) {
              final Iterable<File> sourceFileNames = classToSourceFileGet(subClass);
              if (sourceFileNames != null && !containsAll(myCompiledFiles, sourceFileNames)) {
                if (r.isLocal()) {
                  for (File sourceFileName : sourceFileNames) {
                    debug("Affecting local subclass (introduced field can potentially hide surrounding method parameters/local variables): ", sourceFileName);
                    myAffectedFiles.add(sourceFileName);
                  }
                }
                else {
                  final int outerClass = r.getOuterClassName();
                  if (!isEmpty(outerClass) && myFuture.isFieldVisible(outerClass, f)) {
                    for (File sourceFileName : sourceFileNames) {
                      debug("Affecting inner subclass (introduced field can potentially hide surrounding class fields): ", sourceFileName);
                      myAffectedFiles.add(sourceFileName);
                    }
                  }
                }
              }
            }

            debug("Affecting field usages referenced from subclass ", subClass);
            final IntSet propagated = myFuture.propagateFieldAccess(f.name, subClass);
            myFuture.affectFieldUsages(f, propagated, f.createUsage(myContext, subClass), state.myAffectedUsages, state.myDependants);
            if (f.isStatic()) {
              myFuture.affectStaticMemberOnDemandUsages(subClass, propagated, state.myAffectedUsages, state.myDependants);
            }
            myFuture.appendDependents(subClass, state.myDependants);
          });
        }

        final Collection<Pair<FieldRepr, ClassRepr>> overriddenFields = new HashSet<>();
        myFuture.addOverriddenFields(f, classRepr, overriddenFields, null);

        for (final Pair<FieldRepr, ClassRepr> p : overriddenFields) {
          final FieldRepr ff = p.first;
          final ClassRepr cc = p.second;
          if (ff.isPrivate()) {
            continue;
          }
          final boolean sameKind = f.myType.equals(ff.myType) && f.isStatic() == ff.isStatic() && f.isSynthetic() == ff.isSynthetic() && f.isFinal() == ff.isFinal();
          if (!sameKind || Difference.weakerAccess(f.access, ff.access)) {
            final IntSet propagated = myPresent.propagateFieldAccess(ff.name, cc.name);

            final Set<UsageRepr.Usage> affectedUsages = new HashSet<>();
            debug("Affecting usages of overridden field in class ", cc.name);
            myFuture.affectFieldUsages(ff, propagated, ff.createUsage(myContext, cc.name), affectedUsages, state.myDependants);

            if (sameKind) {
              // check if we can reduce the number of usages going to be recompiled
              UsageConstraint constraint = null;
              if (f.isProtected()) {
                // no need to recompile usages in field class' package and hierarchy, since newly added field is accessible in this scope
                constraint = myFuture.new InheritanceConstraint(cc);
              }
              else if (f.isPackageLocal()) {
                // no need to recompile usages in field class' package, since newly added field is accessible in this scope
                constraint = myFuture.new PackageConstraint(cc.getPackageName());
              }
              if (constraint != null) {
                for (final UsageRepr.Usage usage : affectedUsages) {
                  state.myUsageConstraints.put(usage, constraint);
                }
              }
            }
            state.myAffectedUsages.addAll(affectedUsages);
          }
        }
      }
      debug("End of added fields processing");

      return true;
    }

    private boolean processRemovedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<FieldRepr> removed = diff.fields().removed();
      if (removed.isEmpty()) {
        return true;
      }
      assert myPresent != null;

      debug("Processing removed fields:");

      for (final FieldRepr f : removed) {
        debug("Field: ", f.name);

        if (!myProcessConstantsIncrementally && !f.isPrivate() && (f.access & INLINABLE_FIELD_MODIFIERS_MASK) == INLINABLE_FIELD_MODIFIERS_MASK && f.hasValue()) {
            debug("Field had value and was (non-private) final static => a switch to non-incremental mode requested");
            if (!incrementalDecision(it.name, f, myAffectedFiles, myFilesToCompile, myFilter)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }

        final IntSet propagated = myPresent.propagateFieldAccess(f.name, it.name);
        myPresent.affectFieldUsages(f, propagated, f.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
        if (!f.isPrivate() && f.isStatic()) {
          debug("The field was static --- affecting static field import usages");
          myPresent.affectStaticMemberImportUsages(f.name, it.name, propagated, state.myAffectedUsages, state.myDependants);
        }
      }
      debug("End of removed fields processing");

      return true;
    }

    private boolean processChangedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<Pair<FieldRepr, Difference>> changed = diff.fields().changed();
      if (changed.isEmpty()) {
        return true;
      }
      debug("Processing changed fields:");
      assert myFuture != null;
      assert myPresent != null;

      for (final Pair<FieldRepr, Difference> f : changed) {
        final Difference d = f.second;
        final FieldRepr field = f.first;

        debug("Field: ", field.name);

        final Supplier<IntSet> propagated = lazy(()-> myFuture.propagateFieldAccess(field.name, it.name));

        // only if the field was a compile-time constant
        if (!field.isPrivate() && (field.access & INLINABLE_FIELD_MODIFIERS_MASK) == INLINABLE_FIELD_MODIFIERS_MASK && d.hadValue()) {
          final int changedModifiers = d.addedModifiers() | d.removedModifiers();
          final boolean harmful = (changedModifiers & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != 0;
          final boolean valueChanged = (d.base() & Difference.VALUE) != 0;

          if (harmful || valueChanged || d.accessRestricted()) {
            if (myProcessConstantsIncrementally) {
              debug("Potentially inlined field changed its access or value => affecting field usages and static member import usages");
              myFuture.affectFieldUsages(field, propagated.get(), field.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
              myFuture.affectStaticMemberImportUsages(field.name, it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
            }
            else {
              debug("Potentially inlined field changed its access or value => a switch to non-incremental mode requested");
              if (!incrementalDecision(it.name, field, myAffectedFiles, myFilesToCompile, myFilter)) {
                debug("End of Differentiate, returning false");
                return false;
              }
            }
          }
        }

        if (d.base() != Difference.NONE) {

          if ((d.base() & Difference.TYPE) != 0 || (d.base() & Difference.SIGNATURE) != 0) {
            debug("Type or signature changed --- affecting field usages");
            myFuture.affectFieldUsages(
              field, propagated.get(), field.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants
            );
          }
          else if ((d.base() & Difference.ACCESS) != 0) {
            if ((d.addedModifiers() & Opcodes.ACC_STATIC) != 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) != 0 ||
                (d.addedModifiers() & Opcodes.ACC_PRIVATE) != 0 ||
                (d.addedModifiers() & Opcodes.ACC_VOLATILE) != 0) {
              debug("Added/removed static modifier or added private/volatile modifier --- affecting field usages");
              myFuture.affectFieldUsages(
                field, propagated.get(), field.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants
              );
              if (!field.isPrivate()) {
                if ((d.addedModifiers() & Opcodes.ACC_STATIC) != 0) {
                  debug("Added static modifier --- affecting static member on-demand import usages");
                  myFuture.affectStaticMemberOnDemandUsages(it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
                }
                else if ((d.removedModifiers() & Opcodes.ACC_STATIC) != 0) {
                  debug("Removed static modifier --- affecting static field import usages");
                  myFuture.affectStaticMemberImportUsages(field.name, it.name, propagated.get(), state.myAffectedUsages, state.myDependants);
                }
              }
            }
            else {
              final Set<UsageRepr.Usage> usages = new HashSet<>();

              if ((d.addedModifiers() & Opcodes.ACC_FINAL) != 0) {
                debug("Added final modifier --- affecting field assign usages");
                myFuture.affectFieldUsages(field, propagated.get(), field.createAssignUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
              }

              if ((d.removedModifiers() & Opcodes.ACC_PUBLIC) != 0) {
                debug("Removed public modifier, affecting field usages with appropriate constraint");
                myFuture.affectFieldUsages(field, propagated.get(), field.createUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);

                for (final UsageRepr.Usage usage : usages) {
                  if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) != 0) {
                    state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(it));
                  }
                  else {
                    state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(it.getPackageName()));
                  }
                }
              }
              else if ((d.removedModifiers() & Opcodes.ACC_PROTECTED) != 0 && d.accessRestricted()) {
                debug("Removed protected modifier and the field became less accessible, affecting field usages with package constraint");
                myFuture.affectFieldUsages(field, propagated.get(), field.createUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);

                for (final UsageRepr.Usage usage : usages) {
                  state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(it.getPackageName()));
                }
              }
            }
          }

          if ((d.base() & Difference.ANNOTATIONS) != 0) {
            final Set<AnnotationsChangeTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationsChangeTracker.Recompile.class);
            for (AnnotationsChangeTracker extension : myAnnotationChangeTracker) {
              if (toRecompile.containsAll(AnnotationsChangeTracker.RECOMPILE_ALL)) {
                break;
              }
              final Set<AnnotationsChangeTracker.Recompile> res = extension.fieldAnnotationsChanged(myContext, (ProtoFieldEntity)field, d.annotations());
              if (res.contains(AnnotationsChangeTracker.Recompile.USAGES)) {
                debug("Extension "+extension.getClass().getName()+" requested recompilation because of changes in annotations list --- affecting field usages");
              }
              if (res.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
                debug("Extension "+extension.getClass().getName()+" requested recompilation because of changes in field annotations list --- affecting subclasses");
              }
              toRecompile.addAll(res);
            }
            if (toRecompile.contains(AnnotationsChangeTracker.Recompile.USAGES)) {
              final Set<UsageRepr.Usage> usages = new HashSet<>();
              myFuture.affectFieldUsages(field, propagated.get(), field.createUsage(myContext, it.name), usages, state.myDependants);
              state.myAffectedUsages.addAll(usages);
              // remove any constraints to ensure all field usages are recompiled
              for (UsageRepr.Usage usage : usages) {
                state.myUsageConstraints.remove(usage);
              }
            }
            if (toRecompile.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
              myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
            }
          }

        }
      }
      debug("End of changed fields processing");

      return true;
    }

    private boolean processChangedClasses(final DiffState state) {
      final Collection<Pair<ClassRepr, ClassRepr.Diff>> changedClasses = state.myClassDiff.changed();
      if (!changedClasses.isEmpty()) {
        debug("Processing changed classes:");
        assert myFuture != null;
        assert myPresent != null;

        final Util.FileFilterConstraint fileFilterConstraint = myFilter != null? myPresent.new FileFilterConstraint(myFilter) : null;

        for (final Pair<ClassRepr, ClassRepr.Diff> changed : changedClasses) {
          final ClassRepr changedClass = changed.first;
          final ClassRepr.Diff diff = changed.second;

          myDelta.addChangedClass(changedClass.name);

          debug("Changed: ", changedClass.name);

          final int addedModifiers = diff.addedModifiers();

          final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) != 0;
          final boolean interfacesChanged = !diff.interfaces().unchanged();
          final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) != 0;

          if (superClassChanged) {
            myDelta.registerRemovedSuperClass(changedClass.name, changedClass.getSuperClass().className);

            final ClassRepr newClass = myDelta.getClassReprByName(null, changedClass.name);

            assert (newClass != null);

            myDelta.registerAddedSuperClass(changedClass.name, newClass.getSuperClass().className);
          }

          if (interfacesChanged) {
            for (final TypeRepr.AbstractType typ : diff.interfaces().removed()) {
              myDelta.registerRemovedSuperClass(changedClass.name, ((TypeRepr.ClassType)typ).className);
            }

            for (final TypeRepr.AbstractType typ : diff.interfaces().added()) {
              myDelta.registerAddedSuperClass(changedClass.name, ((TypeRepr.ClassType)typ).className);
            }
          }

          if (myEasyMode) {
            continue;
          }

          final IntSet directDeps = myPresent.appendDependents(changedClass, state.myDependants);

          if (superClassChanged || interfacesChanged || signatureChanged) {
            debug("Superclass changed: ", superClassChanged);
            debug("Interfaces changed: ", interfacesChanged);
            debug("Signature changed ", signatureChanged);

            final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
            final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

            debug("Extends changed: ", extendsChanged);
            debug("Interfaces removed: ", interfacesRemoved);

            myFuture.affectSubclasses(changedClass.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, extendsChanged || interfacesRemoved || signatureChanged, myCompiledFiles, null);

            if (extendsChanged && directDeps != null) {
              final TypeRepr.ClassType excClass = TypeRepr.createClassType(myContext, changedClass.name);
              directDeps.forEach(depClass -> {
                final ClassRepr depClassRepr = myPresent.classReprByName(depClass);
                if (depClassRepr != null) {
                  myPresent.affectMethodUsagesThrowing(depClassRepr, excClass, state.myAffectedUsages, state.myDependants);
                }
              });
            }

            if (!changedClass.isAnonymous()) {
              final IntSet parents = new IntOpenHashSet();
              myPresent.collectSupersRecursively(changedClass.name, parents);
              final IntSet futureParents = new IntOpenHashSet();
              myFuture.collectSupersRecursively(changedClass.name, futureParents);
              parents.removeAll(futureParents);
              parents.remove(myObjectClassName);
              if (!parents.isEmpty()) {
                parents.forEach(className -> {
                  debug("Affecting usages in generic type parameter bounds of class: ", className);
                  final UsageRepr.Usage usage = UsageRepr.createClassAsGenericBoundUsage(myContext, className);
                  state.myAffectedUsages.add(usage);
                  if (fileFilterConstraint != null) {
                    state.myUsageConstraints.put(usage, fileFilterConstraint);
                  }

                  myPresent.appendDependents(className, state.myDependants);
                });
              }
            }
          }

          if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) != 0 || (diff.removedModifiers() & Opcodes.ACC_INTERFACE) != 0) {
            debug("Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
            state.myAffectedUsages.add(changedClass.createUsage());
          }

          if (changedClass.isAnnotation() && changedClass.getRetentionPolicy() == RetentionPolicy.SOURCE) {
            debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
            if (!incrementalDecision(changedClass.getOuterClassName(), changedClass, myAffectedFiles, myFilesToCompile, myFilter)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }

          if ((addedModifiers & Opcodes.ACC_PROTECTED) != 0) {
            debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
            final UsageRepr.Usage usage = changedClass.createUsage();

            state.myAffectedUsages.add(usage);
            state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(changedClass));
          }

          if (diff.packageLocalOn()) {
            debug("Introduction of 'package-private' access detected, adding class usage + package constraint to affected usages");
            final UsageRepr.Usage usage = changedClass.createUsage();

            state.myAffectedUsages.add(usage);
            state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(changedClass.getPackageName()));
          }

          if ((addedModifiers & Opcodes.ACC_FINAL) != 0 || (addedModifiers & Opcodes.ACC_PRIVATE) != 0) {
            debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
            state.myAffectedUsages.add(changedClass.createUsage());
          }

          if ((addedModifiers & Opcodes.ACC_ABSTRACT) != 0 || (addedModifiers & Opcodes.ACC_STATIC) != 0) {
            debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
            state.myAffectedUsages.add(UsageRepr.createClassNewUsage(myContext, changedClass.name));
          }

          if (!changedClass.isAnonymous() && !isEmpty(changedClass.getOuterClassName()) && !changedClass.isPrivate()) {
            if (addedModifiers != 0 || diff.removedModifiers() != 0) {
              debug("Some modifiers (access flags) were changed for non-private inner class, adding class usage to affected usages");
              state.myAffectedUsages.add(changedClass.createUsage());
            }
          }

          if (changedClass.isAnnotation()) {
            debug("Class is annotation, performing annotation-specific analysis");

            if (diff.retentionChanged()) {
              debug("Retention policy change detected, adding class usage to affected usages");
              state.myAffectedUsages.add(changedClass.createUsage());
            }
            else if (diff.targetAttributeCategoryMightChange()) {
              debug("Annotation's attribute category in bytecode might be affected because of TYPE_USE target, adding class usage to affected usages");
              state.myAffectedUsages.add(changedClass.createUsage());
            }
            else {
              final Collection<ElemType> removedtargets = diff.targets().removed();

              if (removedtargets.contains(ElemType.LOCAL_VARIABLE)) {
                debug("Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
                if (!incrementalDecision(changedClass.getOuterClassName(), changedClass, myAffectedFiles, myFilesToCompile, myFilter)) {
                  debug("End of Differentiate, returning false");
                  return false;
                }
              }

              if (!removedtargets.isEmpty()) {
                debug("Removed some annotation targets, adding annotation query");
                state.myAnnotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr.createAnnotationUsage(
                  myContext, TypeRepr.createClassType(myContext, changedClass.name), null, EnumSet.copyOf(removedtargets)
                ));
              }

              for (final MethodRepr m : diff.methods().added()) {
                if (!m.hasValue()) {
                  debug("Added method with no default value: ", m.name);
                  debug("Adding class usage to affected usages");
                  state.myAffectedUsages.add(changedClass.createUsage());
                }
              }
            }

            debug("End of annotation-specific analysis");
          }

          processAddedMethods(state, diff, changedClass);
          processRemovedMethods(state, diff, changedClass);
          processChangedMethods(state, diff, changedClass);

          if (!processAddedFields(state, diff, changedClass)) {
            return false;
          }

          if (!processRemovedFields(state, diff, changedClass)) {
            return false;
          }

          if (!processChangedFields(state, diff, changedClass)) {
            return false;
          }

          if ((diff.base() & Difference.ANNOTATIONS) != 0) {
            final Set<AnnotationsChangeTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationsChangeTracker.Recompile.class);
            for (AnnotationsChangeTracker extension : myAnnotationChangeTracker) {
              if (toRecompile.containsAll(AnnotationsChangeTracker.RECOMPILE_ALL)) {
                break;
              }
              final Set<AnnotationsChangeTracker.Recompile> res = extension.classAnnotationsChanged(myContext, changedClass, diff.annotations());
              if (res.contains(AnnotationsChangeTracker.Recompile.USAGES)) {
                debug("Extension "+extension.getClass().getName()+" requested class usages recompilation because of changes in annotations list --- adding class usage to affected usages");
              }
              if (res.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
                debug("Extension "+extension.getClass().getName()+" requested subclasses recompilation because of changes in annotations list --- adding subclasses to affected usages");
              }
              toRecompile.addAll(res);
            }
            final boolean recompileUsages = toRecompile.contains(AnnotationsChangeTracker.Recompile.USAGES);
            if (recompileUsages) {
              state.myAffectedUsages.add(changedClass.createUsage());
            }
            if (toRecompile.contains(AnnotationsChangeTracker.Recompile.SUBCLASSES)) {
              myFuture.affectSubclasses(changedClass.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, recompileUsages, myCompiledFiles, null);
            }
          }
        }
        debug("End of changed classes processing");
      }

      return !myEasyMode;
    }

    private void processRemovedClases(final DiffState state, @NotNull File fileName) {
      final Collection<ClassRepr> removed = state.myClassDiff.removed();
      if (removed.isEmpty()) {
        return;
      }
      assert myPresent != null;
      assert myDelta.myChangedFiles != null;

      myDelta.myChangedFiles.add(fileName);

      debug("Processing removed classes:");

      for (final ClassRepr c : removed) {
        myDelta.addDeletedClass(c, fileName);
        if (!myEasyMode) {
          myPresent.appendDependents(c, state.myDependants);
          debug("Adding usages of class ", c.name);
          state.myAffectedUsages.add(c.createUsage());
          debug("Affecting usages of removed class ", c.name);
          affectAll(c.name, fileName, myAffectedFiles, myCompiledFiles, myFilter);
        }
      }
      debug("End of removed classes processing.");
    }

    private void processAddedClasses(DiffState state) {
      final Collection<ClassRepr> addedClasses = state.myClassDiff.added();
      if (addedClasses.isEmpty()) {
        return;
      }

      debug("Processing added classes:");

      if (!myEasyMode && myFilter != null) {
        // checking if this newly added class duplicates already existing one
        assert myCompiledFiles != null;
        assert myAffectedFiles != null;

        for (ClassRepr c : addedClasses) {
          if (!c.isLocal() && !c.isAnonymous() && isEmpty(c.getOuterClassName())) {
            final Set<File> candidates = FileCollectionFactory.createCanonicalFileSet();
            final Iterable<File> currentlyMapped = classToSourceFileGet(c.name);
            if (currentlyMapped != null) {
              ContainerUtil.addAll(candidates, currentlyMapped);
            }
            candidates.removeAll(myCompiledFiles);
            final Iterable<File> newSources = myDelta.classToSourceFileGet(c.name);
            if (newSources != null) {
              removeAll(candidates, newSources);
            }
            final Set<File> nonExistentOrOutOfScope = FileCollectionFactory.createCanonicalFileSet();
            for (final File candidate : candidates) {
              if (!candidate.exists() || !myFilter.belongsToCurrentTargetChunk(candidate)) {
                nonExistentOrOutOfScope.add(candidate);
              }
            }
            candidates.removeAll(nonExistentOrOutOfScope);

            if (!candidates.isEmpty()) {
              // Possibly duplicate classes from different sets of source files
              // Schedule for recompilation both to make possible 'duplicate sources' error evident
              candidates.clear(); // just reusing the container
              if (currentlyMapped != null) {
                ContainerUtil.addAll(candidates, currentlyMapped);
              }
              if (newSources != null) {
                ContainerUtil.addAll(candidates, newSources);
              }
              candidates.removeAll(nonExistentOrOutOfScope);

              if (myDebugS.isDebugEnabled()) {
                final StringBuilder msg = new StringBuilder();
                msg.append("Possibly duplicated classes; Scheduling for recompilation sources: ");
                for (File file : candidates) {
                  msg.append(file.getPath()).append("; ");
                }
                debug(msg.toString());
              }

              myAffectedFiles.addAll(candidates);
              return; // do not process this file because it should not be integrated
            }
          }
        }
      }

      for (final ClassRepr c : addedClasses) {
        debug("Class name: ", c.name);
        myDelta.addAddedClass(c);

        for (final TypeRepr.ClassType sup : c.getSuperTypes()) {
          myDelta.registerAddedSuperClass(c.name, sup.className);
        }

        if (!myEasyMode && !c.isAnonymous() && !c.isLocal()) {
          final IntSet toAffect = new IntOpenHashSet();
          toAffect.add(c.name);
          final IntSet classes = myShortClassNameIndex.get(myContext.get(c.getShortName()));
          if (classes != null) {
            // affecting dependencies on all other classes with the same short name
            toAffect.addAll(classes);
          }
          toAffect.forEach(qName -> {
            final IntSet depClasses = myClassToClassDependency.get(qName);
            if (depClasses != null) {
              affectCorrespondingSourceFiles(depClasses);
            }
          });
        }
      }

      debug("End of added classes processing.");
    }

    private void affectCorrespondingSourceFiles(IntSet toAffect) {
      assert myAffectedFiles != null;

      toAffect.forEach(depClass -> {
        final Iterable<File> fNames = classToSourceFileGet(depClass);
        if (fNames != null) {
          for (File fName : fNames) {
            if (myFilter == null || myFilter.accept(fName)) {
              debug("Adding dependent file ", fName);
              myAffectedFiles.add(fName);
            }
          }
        }
      });
    }

    private boolean calculateAffectedFiles(final DiffState state) {
      debug("Checking dependent classes:");
      Collection<? super File> affectedFiles = myAffectedFiles;
      assert affectedFiles != null;
      Collection<? extends File> compiledFiles = myCompiledFiles;
      assert compiledFiles != null;
      final Ref<Boolean> incrementalMode = new Ref<>(Boolean.TRUE);

      IntIterator iterator = state.myDependants.iterator();
      while (iterator.hasNext()) {
        int depClass = iterator.nextInt();
        Iterable<File> depFiles = classToSourceFileGet(depClass);
        if (depFiles != null) {
          for (File depFile : depFiles) {
            processDependentFile(depClass, depFile, incrementalMode, state, affectedFiles, compiledFiles);
            if (!incrementalMode.get()) {
              break;
            }
          }
        }
      }
      return incrementalMode.get();
    }

    private void processDependentFile(int depClass,
                                      @NotNull File depFile,
                                      Ref<Boolean> incrementalMode,
                                      DiffState state,
                                      Collection<? super File> affectedFiles,
                                      Collection<? extends File> compiledFiles) {
      if (affectedFiles.contains(depFile)) {
        return;
      }

      debug("Dependent class: ", depClass);

      final ClassFileRepr repr = getReprByName(depFile, depClass);
      if (repr == null) {
        return;
      }
      if (repr instanceof ClassRepr) {
        final ClassRepr clsRepr = (ClassRepr)repr;
        if (!clsRepr.hasInlinedConstants() && compiledFiles.contains(depFile)) {
          // Classes containing inlined constants from other classes and compiled against older constant values
          // may need to be recompiled several times within a compile session.
          // Otherwise it is safe to skip the file if it has already been compiled in this session.
          return;
        }
        // If among affected files are annotation processor-generated, then we might need to re-generate them.
        // To achieve this, we need to recompile the whole chunk which will cause processors to re-generate these affected files

        if (clsRepr.isGenerated()) {
          debug("Turning non-incremental for the BuildTarget because dependent class is annotation-processor generated");
          incrementalMode.set(Boolean.FALSE);
          return;
        }
      }

      final Set<UsageRepr.Usage> depUsages = repr.getUsages();
      if (depUsages == null || depUsages.isEmpty()) {
        return;
      }

      for (UsageRepr.Usage usage : depUsages) {
        if (usage instanceof UsageRepr.AnnotationUsage) {
          final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)usage;
          for (final UsageRepr.AnnotationUsage query : state.myAnnotationQuery) {
            if (query.satisfies(annotationUsage)) {
              debug("Added file due to annotation query");
              affectedFiles.add(depFile);
              return;
            }
          }
        }
        else if (state.myAffectedUsages.contains(usage)) {
          final UsageConstraint constraint = state.myUsageConstraints.get(usage);
          if (constraint == null) {
            debug("Added file with no constraints");
            affectedFiles.add(depFile);
            return;
          }
          if (constraint.checkResidence(depClass)) {
            debug("Added file with satisfied constraint");
            affectedFiles.add(depFile);
            return;
          }
        }
      }
    }

    boolean differentiate() {
      synchronized (myLock) {
        myDelta.myIsDifferentiated = true;

        if (myDelta.myIsRebuild) {
          return true;
        }

        debug("Begin of Differentiate:");
        debug("Easy mode: ", myEasyMode);

        try {
          processDisappearedClasses();

          final List<FileClasses> newClasses = new ArrayList<>();
          myDelta.myRelativeSourceFilePathToClasses.forEachEntry((relativeFilePath, content) -> {
              File file = toFull(relativeFilePath);
              if (myFilesToCompile == null || myFilesToCompile.contains(file)) {
                // Consider only files actually compiled in this round.
                // For other sources the list of classes taken from this map will be possibly incomplete.
                newClasses.add(new FileClasses(file, content));
              }
              return true;
            });

          for (final FileClasses compiledFile : newClasses) {
            final File fileName = compiledFile.myFileName;
            final Set<ClassRepr> pastClasses = new HashSet<>();
            final Set<ModuleRepr> pastModules = new HashSet<>();
            final Collection<ClassFileRepr> past = sourceFileToClassesGet(fileName);
            if (past != null) {
              for (ClassFileRepr repr : past) {
                if (repr instanceof ClassRepr) {
                  pastClasses.add((ClassRepr)repr);
                }
                else {
                  pastModules.add((ModuleRepr)repr);
                }
              }
            }

            final DiffState state = new DiffState(
              Difference.make(pastClasses, compiledFile.myFileClasses),
              Difference.make(pastModules, compiledFile.myFileModules)
            );

            if (!myEasyMode) {
              processModules(state, fileName);
            }
            if (!processChangedClasses(state)) {
              if (!myEasyMode) {
                // turning non-incremental
                return false;
              }
            }

            processRemovedClases(state, fileName);
            processAddedClasses(state);

            if (!myEasyMode) {
              if (!calculateAffectedFiles(state)) {
                // turning non-incremental
                return false;
              }
            }
          }

          // Now that the list of added classes is complete,
          // check that super-classes of compiled classes are among newly added ones.
          // Even if compiled class did not change, we should register 'added' superclass
          // Consider situation for class B extends A:
          // 1. file A is removed, make fails with error in file B
          // 2. A is added back, B and A are compiled together in the second make session
          // 3. Even if B did not change, A is considered as newly added and should be registered again in ClassToSubclasses dependencies
          //    Without this code such registration will not happen because list of B's parents did not change
          final Set<ClassRepr> addedClasses = myDelta.getAddedClasses();
          if (!addedClasses.isEmpty()) {
            final IntSet addedNames = new IntOpenHashSet();
            for (ClassRepr repr : addedClasses) {
              addedNames.add(repr.name);
            }
            for (FileClasses compiledFile : newClasses) {
              for (ClassRepr aClass : compiledFile.myFileClasses) {
                for (TypeRepr.ClassType parent : aClass.getSuperTypes()) {
                  if (addedNames.contains(parent.className)) {
                    myDelta.registerAddedSuperClass(aClass.name, parent.className);
                  }
                }
              }
            }
          }

          debug("End of Differentiate.");

          if (myEasyMode) {
            return false;
          }
          assert myAffectedFiles != null;

          final Collection<String> removed = myDelta.myRemovedFiles;
          if (removed != null) {
            for (final String r : removed) {
              myAffectedFiles.remove(new File(r));
            }
          }
          return true;
        }
        finally {
          if (myFilesToCompile != null) {
            assert myDelta.myChangedFiles != null;
            // if some class is associated with several sources,
            // some of them may not have been compiled in this round, so such files should be considered unchanged
            myDelta.myChangedFiles.retainAll(myFilesToCompile);
          }
        }
      }
    }

    private void processModules(final DiffState state, File fileName) {
      final Difference.Specifier<ModuleRepr, ModuleRepr.Diff> modulesDiff = state.myModulesDiff;
      if (modulesDiff.unchanged()) {
        return;
      }

      for (ModuleRepr moduleRepr : modulesDiff.added()) {
        myDelta.addChangedClass(moduleRepr.name); // need this for integrate
        // after module has been added, the whole target should be rebuilt
        // because necessary 'require' directives may be missing from the newly added module-info file
        myFuture.affectModule(moduleRepr, myAffectedFiles);
      }

      for (ModuleRepr removedModule : modulesDiff.removed()) {
        myDelta.addDeletedClass(removedModule, fileName); // need this for integrate
        myPresent.affectDependentModules(state, removedModule.name, null, true);
      }

      for (Pair<ModuleRepr, ModuleRepr.Diff> pair : modulesDiff.changed()) {
        final ModuleRepr moduleRepr = pair.first;
        final ModuleRepr.Diff d = pair.second;
        boolean affectSelf = false;
        boolean affectDeps = false;
        UsageConstraint constraint = null;

        myDelta.addChangedClass(moduleRepr.name); // need this for integrate

        if (d.versionChanged()) {
          final int version = moduleRepr.getVersion();
          myPresent.affectDependentModules(state, moduleRepr.name, new UsageConstraint() {
            @Override
            public boolean checkResidence(int dep) {
              final ModuleRepr depModule = myPresent.moduleReprByName(dep);
              if (depModule != null) {
                for (ModuleRequiresRepr requires : depModule.getRequires()) {
                  if (requires.name == moduleRepr.name && requires.getVersion() == version) {
                    return true;
                  }
                }
              }
              return false;
            }
          }, false);
        }

        final Difference.Specifier<ModuleRequiresRepr, ModuleRequiresRepr.Diff> requiresDiff = d.requires();
        for (ModuleRequiresRepr removed : requiresDiff.removed()) {
          affectSelf = true;
          if (removed.isTransitive()) {
            affectDeps = true;
            constraint = UsageConstraint.ANY;
            break;
          }
        }
        for (Pair<ModuleRequiresRepr, ModuleRequiresRepr.Diff> changed : requiresDiff.changed()) {
          affectSelf |= changed.second.versionChanged();
          if (changed.second.becameNonTransitive()) {
            affectDeps = true;
            // we could have created more precise constraint here: analyze if required module (recursively)
            // has only qualified exports that include given module's name. But this seems to be excessive since
            // in most cases module's exports are unqualified, so that any other module can access the exported API.
            constraint = UsageConstraint.ANY;
          }
        }

        final Difference.Specifier<ModulePackageRepr, ModulePackageRepr.Diff> exportsDiff = d.exports();

        if (!affectDeps) {
          for (ModulePackageRepr removedPackage : exportsDiff.removed()) {
            affectDeps = true;
            if (!removedPackage.isQualified()) {
              constraint = UsageConstraint.ANY;
              break;
            }
            for (Integer name : removedPackage.getModuleNames()) {
              final UsageConstraint matchName = UsageConstraint.exactMatch(name);
              if (constraint == null) {
                constraint = matchName;
              }
              else {
                constraint = constraint.or(matchName);
              }
            }
          }
        }

        if (!affectDeps || constraint != UsageConstraint.ANY) {
          for (Pair<ModulePackageRepr, ModulePackageRepr.Diff> p : exportsDiff.changed()) {
            final Collection<Integer> removedModuleNames = p.second.targetModules().removed();
            affectDeps |= !removedModuleNames.isEmpty();
            if (affectDeps) {
              for (Integer name : removedModuleNames) {
                final UsageConstraint matchName = UsageConstraint.exactMatch(name);
                if (constraint == null) {
                  constraint = matchName;
                }
                else {
                  constraint = constraint.or(matchName);
                }
              }
            }
          }
        }

        if (affectSelf) {
          myPresent.affectModule(moduleRepr, myAffectedFiles);
        }
        if (affectDeps) {
          myPresent.affectDependentModules(state, moduleRepr.name, constraint, true);
        }
      }
    }
  }

  public void differentiateOnRebuild(final Mappings delta) {
    new Differential(delta).differentiate();
  }

  public void differentiateOnNonIncrementalMake(final Mappings delta,
                                                final Collection<String> removed,
                                                final Collection<? extends File> filesToCompile) {
    new Differential(delta, removed, filesToCompile).differentiate();
  }

  public boolean differentiateOnIncrementalMake
    (final Mappings delta,
     final Collection<String> removed,
     final Collection<? extends File> filesToCompile,
     final Collection<? extends File> compiledWithErrors,
     final Collection<? extends File> compiledFiles,
     final Collection<? super File> affectedFiles,
     @NotNull final DependentFilesFilter filter) {
    return new Differential(delta, removed, filesToCompile, compiledWithErrors, compiledFiles, affectedFiles, filter).differentiate();
  }

  private void cleanupBackDependency(final int className, @Nullable Set<? extends UsageRepr.Usage> usages, final IntIntMultiMaplet buffer) {
    if (usages == null) {
      final ClassFileRepr repr = getReprByName(null, className);
      if (repr != null) {
        usages = repr.getUsages();
      }
    }

    if (usages != null) {
      for (final UsageRepr.Usage u : usages) {
        final int owner = u.getOwner();
        if (owner != className) {
          buffer.put(owner, className);
        }
      }
    }
  }

  private void cleanupRemovedClass(final Mappings delta, @NotNull final ClassFileRepr cr, File sourceFile, final Set<? extends UsageRepr.Usage> usages, final IntIntMultiMaplet dependenciesTrashBin) {
    final int className = cr.name;

    // it is safe to cleanup class information if it is mapped to non-existing files only
    final Iterable<File> _currentlyMapped = classToSourceFileGet(className);
    if (_currentlyMapped == null) {
      return;
    }
    final Collection<File> currentlyMapped = ContainerUtil.collect(_currentlyMapped.iterator());
    if (currentlyMapped.isEmpty()) {
      return;
    }
    if (currentlyMapped.size() == 1) {
      if (!FileUtil.filesEqual(sourceFile, currentlyMapped.iterator().next())) {
        // if classname is already mapped to a different source, the class with such FQ name exists elsewhere, so
        // we cannot destroy all these links
        return;
      }
    }
    else {
      // many files
      for (File file : currentlyMapped) {
        if (!FileUtil.filesEqual(sourceFile, file) && file.exists()) {
          return;
        }
      }
    }

    if (cr instanceof ClassRepr) {
      for (final TypeRepr.ClassType superSomething : ((ClassRepr)cr).getSuperTypes()) {
        delta.registerRemovedSuperClass(className, superSomething.className);
      }
    }

    cleanupBackDependency(className, usages, dependenciesTrashBin);

    myClassToClassDependency.remove(className);
    myClassToSubclasses.remove(className);
    myClassToRelativeSourceFilePath.remove(className);
    if (cr instanceof ClassRepr) {
      final ClassRepr _cr = (ClassRepr)cr;
      if (!_cr.isLocal() && !_cr.isAnonymous()) {
        myShortClassNameIndex.removeFrom(myContext.get(_cr.getShortName()), className);
      }
    }
  }

  public void integrate(final Mappings delta) {
    synchronized (myLock) {
      try {
        assert (delta.isDifferentiated());

        final Collection<String> removed = delta.myRemovedFiles;

        delta.runPostPasses();

        final IntIntMultiMaplet dependenciesTrashBin = new IntIntTransientMultiMaplet();

        if (removed != null) {
          for (final String file : removed) {
            final File deletedFile = new File(file);
            final Set<ClassFileRepr> fileClasses = (Set<ClassFileRepr>)sourceFileToClassesGet(deletedFile);

            if (fileClasses != null) {
              for (final ClassFileRepr aClass : fileClasses) {
                cleanupRemovedClass(delta, aClass, deletedFile, aClass.getUsages(), dependenciesTrashBin);
              }
              myRelativeSourceFilePathToClasses.remove(myRelativizer.toRelative(file));
            }
          }
        }

        if (!delta.isRebuild()) {
          for (final Pair<ClassFileRepr, File> pair : delta.getDeletedClasses()) {
            final ClassFileRepr deletedClass = pair.first;
            cleanupRemovedClass(delta, deletedClass, pair.second, deletedClass.getUsages(), dependenciesTrashBin);
          }
          for (ClassRepr repr : delta.getAddedClasses()) {
            if (!repr.isAnonymous() && !repr.isLocal()) {
              myShortClassNameIndex.put(myContext.get(repr.getShortName()), repr.name);
            }
          }

          final IntSet superClasses = new IntOpenHashSet();
          final IntIntTransientMultiMaplet addedSuperClasses = delta.getAddedSuperClasses();
          final IntIntTransientMultiMaplet removedSuperClasses = delta.getRemovedSuperClasses();

          addAllKeys(superClasses, addedSuperClasses);
          addAllKeys(superClasses, removedSuperClasses);

          superClasses.forEach(superClass -> {
            final IntSet added = addedSuperClasses.get(superClass);
            IntSet removed12 = removedSuperClasses.get(superClass);

            final IntSet old = myClassToSubclasses.get(superClass);

            if (old == null) {
              if (added != null && !added.isEmpty()) {
                myClassToSubclasses.replace(superClass, added);
              }
            }
            else {
              boolean changed = false;
              final int[] addedAsArray = added == null || added.isEmpty() ? null : added.toIntArray();
              if (removed12 != null && !removed12.isEmpty()) {
                if (addedAsArray != null) {
                  // optimization: avoid unnecessary changes in the set
                  removed12 = new IntOpenHashSet(removed12);
                  removed12.removeAll(IntSet.of(addedAsArray));
                }
                if (!removed12.isEmpty()) {
                  changed = old.removeAll(removed12);
                }
              }

              if (addedAsArray != null) {
                changed |= old.addAll(IntSet.of(addedAsArray));
              }

              if (changed) {
                myClassToSubclasses.replace(superClass, old);
              }
            }
          });

          delta.getChangedClasses().forEach(className -> {
            final Collection<String> sourceFiles = delta.myClassToRelativeSourceFilePath.get(className);
            myClassToRelativeSourceFilePath.replace(className, sourceFiles);

            cleanupBackDependency(className, null, dependenciesTrashBin);
          });

          delta.getChangedFiles().forEach(fileName -> {
            String relative = toRelative(fileName);
            Collection<ClassFileRepr> classes = delta.myRelativeSourceFilePathToClasses.get(relative);
            myRelativeSourceFilePathToClasses.replace(relative, classes);
          });

          // some classes may be associated with multiple sources.
          // In case some of these sources was not compiled, but the class was changed, we need to update
          // sourceToClasses mapping for such sources to include the updated ClassRepr version of the changed class
          Set<File> unchangedSources = FileCollectionFactory.createCanonicalFileSet();
          delta.myRelativeSourceFilePathToClasses.forEachEntry((source, b) -> {
            unchangedSources.add(toFull(source));
            return true;
          });
          unchangedSources.removeAll(delta.getChangedFiles());
          if (!unchangedSources.isEmpty()) {
            unchangedSources.forEach(unchangedSource -> {
              final Collection<ClassFileRepr> updatedClasses = delta.sourceFileToClassesGet(unchangedSource);
              if (updatedClasses != null && !updatedClasses.isEmpty()) {
                final List<ClassFileRepr> classesToPut = new ArrayList<>();
                final IntSet updatedClassNames = new IntOpenHashSet();
                for (ClassFileRepr aClass : updatedClasses) {
                  // from all generated classes on this round consider only 'differentiated' ones, for
                  // which we can reliably say that the class has changed. Keep classes, for which no such checks were made,
                  // to make it possible to create a diff and compare changes on next compilation rounds.
                  if (delta.getChangedClasses().contains(aClass.name)) {
                    classesToPut.add(aClass);
                    updatedClassNames.add(aClass.name);
                  }
                }
                final Collection<ClassFileRepr> currentClasses = sourceFileToClassesGet(unchangedSource);
                if (currentClasses != null) {
                  for (ClassFileRepr aClass : currentClasses) {
                    if (!updatedClassNames.contains(aClass.name)) {
                      classesToPut.add(aClass);
                    }
                  }
                }
                myRelativeSourceFilePathToClasses.replace(toRelative(unchangedSource), classesToPut);
              }
            });
          }
        }
        else {
          myClassToSubclasses.putAll(delta.myClassToSubclasses);
          myClassToRelativeSourceFilePath.replaceAll(delta.myClassToRelativeSourceFilePath);
          myRelativeSourceFilePathToClasses.replaceAll(delta.myRelativeSourceFilePathToClasses);
          delta.myRelativeSourceFilePathToClasses.forEachEntry(
            (src, classes) -> {
              for (ClassFileRepr repr : classes) {
                if (repr instanceof ClassRepr) {
                  final ClassRepr clsRepr = (ClassRepr)repr;
                  if (!clsRepr.isAnonymous() && !clsRepr.isLocal()) {
                    myShortClassNameIndex.put(myContext.get(clsRepr.getShortName()), repr.name);
                  }
                }
              }
              return true;
            });
        }

        // updating classToClass dependencies

        final IntSet affectedClasses = new IntOpenHashSet();

        addAllKeys(affectedClasses, dependenciesTrashBin);
        addAllKeys(affectedClasses, delta.myClassToClassDependency);

        affectedClasses.forEach(aClass -> {
          final IntSet toRemove = dependenciesTrashBin.get(aClass);
          if (toRemove != null) {
            myClassToClassDependency.removeAll(aClass, toRemove);
          }
          final IntSet toAdd = delta.myClassToClassDependency.get(aClass);
          if (toAdd != null) {
            myClassToClassDependency.put(aClass, toAdd);
          }
        });
      }
      finally {
        delta.close();
      }
    }
  }

  public Callbacks.Backend getCallback() {
    return new Callbacks.Backend() {
      // className -> {imports; static_imports}
      private final Map<String, Pair<Collection<String>, Collection<String>>> myImportRefs = Collections.synchronizedMap(new HashMap<>());
      private final Map<String, Collection<Callbacks.ConstantRef>> myConstantRefs = Collections.synchronizedMap(new HashMap<>());

      @Override
      public void associate(String classFileName, Collection<String> sources, ClassReader cr, boolean isGenerated) {
        synchronized (myLock) {
          final int classFileNameS = myContext.get(classFileName);
          final ClassFileRepr result = new ClassfileAnalyzer(myContext).analyze(classFileNameS, cr, isGenerated);
          if (result != null) {
            // since java9 'repr' can represent either a class or a compiled module-info.java
            final int className = result.name;

            if (result instanceof ClassRepr) {
              final ClassRepr classRepr = (ClassRepr)result;
              final String classNameStr = myContext.getValue(className);
              if (addConstantUsages(classRepr, myConstantRefs.remove(classNameStr))) {
                // Important: should register constants before imports, because imports can produce additional
                // field references too and addConstantUsages may return false in this case
                classRepr.setHasInlinedConstants(true);
              }
              final Pair<Collection<String>, Collection<String>> imports = myImportRefs.remove(classNameStr);
              if (imports != null) {
                addImportUsages(classRepr, imports.getFirst(), imports.getSecond());
              }
            }

            for (String sourceFileName : sources) {
              String relative = myRelativizer.toRelative(sourceFileName);
              myClassToRelativeSourceFilePath.put(className, relative);
              myRelativeSourceFilePathToClasses.put(relative, result);
            }

            if (result instanceof ClassRepr) {
              for (final TypeRepr.ClassType s : ((ClassRepr)result).getSuperTypes()) {
                myClassToSubclasses.put(s.className, className);
              }
            }

            for (final UsageRepr.Usage u : result.getUsages()) {
              final int owner = u.getOwner();
              if (owner != className) {
                myClassToClassDependency.put(owner, className);
              }
            }
          }
        }
      }

      @Override
      public void registerImports(String className, Collection<String> classImports, Collection<String> staticImports) {
        final String key = className.replace('.', '/');
        if (!classImports.isEmpty() || !staticImports.isEmpty()) {
          myImportRefs.put(key, Pair.create(classImports, staticImports));
        }
        else {
          myImportRefs.remove(key);
        }
      }

      @Override
      public void registerConstantReferences(String className, Collection<Callbacks.ConstantRef> cRefs) {
        final String key = className.replace('.', '/');
        if (!cRefs.isEmpty()) {
          myConstantRefs.put(key, cRefs);
        }
        else {
          myConstantRefs.remove(key);
        }
      }

      private void addImportUsages(ClassRepr repr, Collection<String> classImports, Collection<String> staticImports) {
        for (final String anImport : classImports) {
          if (!anImport.endsWith(IMPORT_WILDCARD_SUFFIX)) {
            repr.addUsage(UsageRepr.createClassUsage(myContext, myContext.get(anImport.replace('.', '/'))));
          }
        }
        for (String anImport : staticImports) {
          if (anImport.endsWith(IMPORT_WILDCARD_SUFFIX)) {
            final int iname = myContext.get(anImport.substring(0, anImport.length() - IMPORT_WILDCARD_SUFFIX.length()).replace('.', '/'));
            repr.addUsage(UsageRepr.createClassUsage(myContext, iname));
            repr.addUsage(UsageRepr.createImportStaticOnDemandUsage(myContext, iname));
          }
          else {
            final int i = anImport.lastIndexOf('.');
            if (i > 0 && i < anImport.length() - 1) {
              final int iname = myContext.get(anImport.substring(0, i).replace('.', '/'));
              final int memberName = myContext.get(anImport.substring(i+1));
              repr.addUsage(UsageRepr.createClassUsage(myContext, iname));
              repr.addUsage(UsageRepr.createImportStaticMemberUsage(myContext, memberName, iname));
            }
          }
        }
      }

      private boolean addConstantUsages(ClassRepr repr, Collection<Callbacks.ConstantRef> cRefs) {
        boolean addedNewUsages = false;
        if (cRefs != null) {
          for (Callbacks.ConstantRef ref : cRefs) {
            final int owner = myContext.get(ref.getOwner().replace('.', '/'));
            if (repr.name != owner) {
              addedNewUsages |= repr.addUsage(UsageRepr.createFieldUsage(myContext, myContext.get(ref.getName()), owner, myContext.get(ref.getDescriptor())));
            }
          }
        }
        return addedNewUsages;
      }
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    final File f = new File(sourceFileName);
    synchronized (myLock) {
      final Collection<ClassFileRepr> reprs = sourceFileToClassesGet(f);
      if (reprs == null || reprs.isEmpty()) {
        return null;
      }
      final Set<ClassRepr> result = new HashSet<>();
      for (ClassFileRepr repr : reprs) {
        if (repr instanceof ClassRepr) {
          result.add((ClassRepr)repr);
        }
      }
      return result;
    }
  }

  public @NotNull Collection<File> getClassSources(int className) {
    synchronized (myLock) {
      final Iterable<File> files = classToSourceFileGet(className);
      return files == null? Collections.emptyList() : ContainerUtil.collect(files.iterator());
    }
  }

  public void close() {
    BuildDataCorruptedException error = null;
    synchronized (myLock) {
      for (CloseableMaplet maplet : Arrays.asList(myClassToSubclasses, myClassToClassDependency, myRelativeSourceFilePathToClasses, myClassToRelativeSourceFilePath, myShortClassNameIndex)) {
        if (maplet != null) {
          try {
            maplet.close();
          }
          catch (BuildDataCorruptedException ex) {
            if (error == null) {
              error = ex;
            }
          }
        }
      }

      if (!myIsDelta) {
        // only close if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          try {
            context.close();
          }
          catch (BuildDataCorruptedException ex) {
            if (error == null) {
              error = ex;
            }
          }
          myContext = null;
        }
      }
    }
    if (error != null) {
      throw error;
    }
  }

  public void flush(final boolean memoryCachesOnly) {
    synchronized (myLock) {
      myClassToSubclasses.flush(memoryCachesOnly);
      myClassToClassDependency.flush(memoryCachesOnly);
      myRelativeSourceFilePathToClasses.flush(memoryCachesOnly);
      myClassToRelativeSourceFilePath.flush(memoryCachesOnly);

      if (!myIsDelta) {
        myShortClassNameIndex.flush(memoryCachesOnly);
        // flush if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          context.clearMemoryCaches();
          if (!memoryCachesOnly) {
            context.flush();
          }
        }
      }
    }
  }

  private static void addAllKeys(final IntSet whereToAdd, final IntIntMultiMaplet maplet) {
    maplet.forEachEntry((integers, value) -> whereToAdd.add(value));
  }

  private void registerAddedSuperClass(final int aClass, final int superClass) {
    assert (myAddedSuperClasses != null);
    myAddedSuperClasses.put(superClass, aClass);
  }

  private void registerRemovedSuperClass(final int aClass, final int superClass) {
    assert (myRemovedSuperClasses != null);
    myRemovedSuperClasses.put(superClass, aClass);
  }

  private boolean isDifferentiated() {
    return myIsDifferentiated;
  }

  private boolean isRebuild() {
    return myIsRebuild;
  }

  private void addDeletedClass(final ClassFileRepr cr, File fileName) {
    assert (myDeletedClasses != null);

    myDeletedClasses.add(Pair.create(cr, fileName));

    addChangedClass(cr.name);
  }

  private void addAddedClass(final ClassRepr cr) {
    assert (myAddedClasses != null);

    myAddedClasses.add(cr);

    addChangedClass(cr.name);
  }

  private void addChangedClass(final int it) {
    assert (myChangedClasses != null && myChangedFiles != null);
    myChangedClasses.add(it);

    final Iterable<File> files = classToSourceFileGet(it);

    if (files != null) {
      ContainerUtil.addAll(myChangedFiles, files);
    }
  }

  @NotNull
  private Set<Pair<ClassFileRepr, File>> getDeletedClasses() {
    return myDeletedClasses == null ? Collections.emptySet() : Collections.unmodifiableSet(myDeletedClasses);
  }

  @NotNull
  private Set<ClassRepr> getAddedClasses() {
    return myAddedClasses == null ? Collections.emptySet() : Collections.unmodifiableSet(myAddedClasses);
  }

  private IntSet getChangedClasses() {
    return myChangedClasses;
  }

  private Set<File> getChangedFiles() {
    return myChangedFiles;
  }

  private static class OverloadDescriptor {
    final int accessScope;
    final MethodRepr overloadMethod;
    final ClassRepr overloadMethodOwner;

    OverloadDescriptor(int accessScope, MethodRepr overloadMethod, ClassRepr overloadMethodOwner) {
      this.accessScope = accessScope;
      this.overloadMethod = overloadMethod;
      this.overloadMethodOwner = overloadMethodOwner;
    }
  }

  private static void debug(final String s) {
    LOG.debug(s);
  }

  private void debug(final String comment, final int s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final File f) {
    debug(comment, f.getPath());
  }

  private void debug(final String comment, final String s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final boolean s) {
    myDebugS.debug(comment, s);
  }

  public void toStream(final PrintStream stream) {
    final Streamable[] data = {
      myClassToSubclasses,
      myClassToClassDependency,
      myRelativeSourceFilePathToClasses,
      myClassToRelativeSourceFilePath,
    };

    final String[] info = {
      "ClassToSubclasses",
      "ClassToClassDependency",
      "SourceFileToClasses",
      "ClassToSourceFile",
      "SourceFileToAnnotationUsages",
      "SourceFileToUsages"
    };

    for (int i = 0; i < data.length; i++) {
      stream.print("Begin Of ");
      stream.println(info[i]);

      data[i].toStream(myContext, stream);

      stream.print("End Of ");
      stream.println(info[i]);
    }
  }

  private static <T> boolean containsAll(final Collection<? extends T> collection, Iterable<? extends T> it) {
    for (T file : it) {
      if (!collection.contains(file)) {
        return false;
      }
    }
    return true;
  }

  private static <T> void removeAll(final Collection<? extends T> collection, Iterable<? extends T> it) {
    for (T file : it) {
      collection.remove(file);
    }
  }

  private static <T> Supplier<T> lazy(Supplier<T> calculation) {
    return new Supplier<T>() {
      Ref<T> calculated;
      @Override
      public T get() {
        return (calculated != null? calculated : (calculated = new Ref<>(calculation.get()))).get();
      }
    };
  }

  public void toStream(File outputRoot) {
    final Streamable[] data = {
      myClassToSubclasses,
      myClassToClassDependency,
      myRelativeSourceFilePathToClasses,
      myClassToRelativeSourceFilePath,
      myShortClassNameIndex
    };

    final String[] info = {
      "ClassToSubclasses",
      "ClassToClassDependency",
      "SourceFileToClasses",
      "ClassToSourceFile",
      "ShortClassNameIndex"
    };

    for (int i = 0; i < data.length; i++) {
      final File file = new File(outputRoot, info[i]);
      FileUtil.createIfDoesntExist(file);
      try (PrintStream stream = new PrintStream(file)) {
        data[i].toStream(myContext, stream);
      }
      catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }
}