/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IntInlineKeyDescriptor;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.storage.FileKeyDescriptor;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author: db
 * Date: 28.01.11
 */
public class Mappings {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.ether.dependencyView.Mappings");

  private final static String CLASS_TO_SUBCLASSES = "classToSubclasses.tab";
  private final static String CLASS_TO_CLASS = "classToClass.tab";
  private final static String SHORT_NAMES = "shortNames.tab";
  private final static String SOURCE_TO_CLASS = "sourceToClass.tab";
  private final static String CLASS_TO_SOURCE = "classToSource.tab";
  private static final IntInlineKeyDescriptor INT_KEY_DESCRIPTOR = new IntInlineKeyDescriptor();
  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;
  private static final CollectionFactory<ClassRepr> ourClassSetConstructor = new CollectionFactory<ClassRepr>() {
    public Set<ClassRepr> create() {
      // for IDEA codebase on average there is no more than 2.5 classes out of one source file, so we use smaller estimate
      return new THashSet<ClassRepr>(5, DEFAULT_SET_LOAD_FACTOR);
    }
  };

  private final boolean myIsDelta;
  private final boolean myDeltaIsTransient;
  private boolean myIsDifferentiated = false;
  private boolean myIsRebuild = false;

  private final TIntHashSet myChangedClasses;
  private final THashSet<File> myChangedFiles;
  private final Set<Pair<ClassRepr, File>> myDeletedClasses;
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
  private ObjectObjectMultiMaplet<File, ClassRepr> mySourceFileToClasses;
  private IntObjectMultiMaplet<File> myClassToSourceFile;
  /**
   * [short className] -> list of FQ names
   */
  private IntIntMultiMaplet myShortClassNameIndex;

  private IntIntTransientMultiMaplet myRemovedSuperClasses;
  private IntIntTransientMultiMaplet myAddedSuperClasses;

  @Nullable
  private Collection<String> myRemovedFiles;

  private Mappings(final Mappings base) throws IOException {
    myLock = base.myLock;
    myIsDelta = true;
    myChangedClasses = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myChangedFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    myDeletedClasses = new HashSet<Pair<ClassRepr, File>>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myAddedClasses = new HashSet<ClassRepr>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myDeltaIsTransient = base.myDeltaIsTransient;
    myRootDir = new File(FileUtil.toSystemIndependentName(base.myRootDir.getAbsolutePath()) + File.separatorChar + "myDelta");
    myContext = base.myContext;
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
    myObjectClassName = myContext.get("java/lang/Object");
    myDebugS = base.myDebugS;
    createImplementation();
  }

  public Mappings(final File rootDir, final boolean transientDelta) throws IOException {
    myLock = new Object();
    myIsDelta = false;
    myChangedClasses = null;
    myChangedFiles = null;
    myDeletedClasses = null;
    myAddedClasses = null;
    myDeltaIsTransient = transientDelta;
    myRootDir = rootDir;
    createImplementation();
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
    myObjectClassName = myContext.get("java/lang/Object");
  }

  private void createImplementation() throws IOException {
    if (!myIsDelta) {
      myContext = new DependencyContext(myRootDir);
      myDebugS = myContext.getLogger(LOG);
    }

    myRemovedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;
    myAddedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;

    final CollectionFactory<File> fileCollectionFactory = new CollectionFactory<File>() {
      public Collection<File> create() {
        return new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY); // todo: do we really need set and not a list here?
      }
    };
    if (myIsDelta && myDeltaIsTransient) {
      myClassToSubclasses = new IntIntTransientMultiMaplet();
      myClassToClassDependency = new IntIntTransientMultiMaplet();
      myShortClassNameIndex = null;
      mySourceFileToClasses = new ObjectObjectTransientMultiMaplet<File, ClassRepr>(FileUtil.FILE_HASHING_STRATEGY, ourClassSetConstructor);
      myClassToSourceFile = new IntObjectTransientMultiMaplet<File>(fileCollectionFactory);
    }
    else {
      if (myIsDelta) {
        myRootDir.mkdirs();
      }
      myClassToSubclasses = new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_SUBCLASSES), INT_KEY_DESCRIPTOR);
      myClassToClassDependency = new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS), INT_KEY_DESCRIPTOR);
      myShortClassNameIndex = myIsDelta? null : new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, SHORT_NAMES), INT_KEY_DESCRIPTOR);
      mySourceFileToClasses = new ObjectObjectPersistentMultiMaplet<File, ClassRepr>(
        DependencyContext.getTableFile(myRootDir, SOURCE_TO_CLASS), new FileKeyDescriptor(), ClassRepr.externalizer(myContext),
        ourClassSetConstructor
      );
      myClassToSourceFile = new IntObjectPersistentMultiMaplet<File>(DependencyContext.getTableFile(myRootDir, CLASS_TO_SOURCE), INT_KEY_DESCRIPTOR, new FileKeyDescriptor(), fileCollectionFactory);
    }
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

  private void compensateRemovedContent(final Collection<File> compiled) {
    if (compiled != null) {
      for (final File file : compiled) {
        if (!mySourceFileToClasses.containsKey(file)) {
          mySourceFileToClasses.put(file, new HashSet<ClassRepr>());
        }
      }
    }
  }

  @Nullable
  private ClassRepr getReprByName(final @Nullable File source, final int qName) {
    final Collection<File> sources = source != null? Collections.singleton(source) : myClassToSourceFile.get(qName);
    if (sources != null) {
      for (File src : sources) {
        final Collection<ClassRepr> reprs = mySourceFileToClasses.get(src);

        if (reprs != null) {
          for (ClassRepr repr : reprs) {
            if (repr.name == qName) {
              return repr;
            }
          }
        }
      }
    }

    return null;
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

  private final LinkedBlockingQueue<Runnable> myPostPasses = new LinkedBlockingQueue<Runnable>();

  private void runPostPasses() {
    final Set<Pair<ClassRepr, File>> deleted = myDeletedClasses;
    if (deleted != null) {
      for (Pair<ClassRepr, File> pair : deleted) {
        final int deletedClassName = pair.first.name;
        final Collection<File> sources = myClassToSourceFile.get(deletedClassName);
        if (sources == null || sources.isEmpty()) { // if really deleted and not e.g. moved 
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

  private interface MemberComparator {
    boolean isSame(ProtoMember member);
  }

  private class Util {
    @Nullable
    private final Mappings myMappings;

    private Util() {
      myMappings = null;
    }

    private Util(@NotNull Mappings mappings) {
      myMappings = mappings;
    }

    void appendDependents(final ClassRepr c, final TIntHashSet result) {
      final TIntHashSet depClasses = myClassToClassDependency.get(c.name);

      if (depClasses != null) {
        addAll(result, depClasses);
      }
    }

    void propagateMemberAccessRec(final TIntHashSet acc, final boolean isField, final boolean root, final MemberComparator comparator, final int reflcass) {
      final ClassRepr repr = reprByName(reflcass);
      if (repr != null) {
        if (!root) {
          final Set<? extends ProtoMember> members = isField ? repr.getFields() : repr.getMethods();

          for (ProtoMember m : members) {
            if (comparator.isSame(m)) {
              return;
            }
          }

          if (!acc.add(reflcass)) {
            return; // SOE prevention
          }
        }

        final TIntHashSet subclasses = myClassToSubclasses.get(reflcass);

        if (subclasses != null) {
          subclasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subclass) {
              propagateMemberAccessRec(acc, isField, false, comparator, subclass);
              return true;
            }
          });
        }
      }
    }

    TIntHashSet propagateMemberAccess(final boolean isField, final MemberComparator comparator, final int className) {
      final TIntHashSet acc = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
      propagateMemberAccessRec(acc, isField, true, comparator, className);
      return acc;
    }

    TIntHashSet propagateFieldAccess(final int name, final int className) {
      return propagateMemberAccess(true, new MemberComparator() {
        @Override
        public boolean isSame(ProtoMember member) {
          return member.name == name;
        }
      }, className);
    }

    TIntHashSet propagateMethodAccess(final MethodRepr m, final int className) {
      return propagateMemberAccess(false, new MemberComparator() {
        @Override
        public boolean isSame(ProtoMember member) {
          if (member instanceof MethodRepr) {
            final MethodRepr memberMethod = (MethodRepr)member;
            return memberMethod.name == m.name && Arrays.equals(memberMethod.myArgumentTypes, m.myArgumentTypes);
          }
          return member.name == m.name;
        }
      }, className);
    }

    MethodRepr.Predicate lessSpecific(final MethodRepr than) {
      return new MethodRepr.Predicate() {
        @Override
        public boolean satisfy(final MethodRepr m) {
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
        }
      };
    }

    private void addOverridingMethods(final MethodRepr m, final ClassRepr fromClass, final MethodRepr.Predicate predicate, final Collection<Pair<MethodRepr, ClassRepr>> container, TIntHashSet visitedClasses) {
      if (m.name == myInitName) {
        return; // overriding is not defined for constructors
      }
      final TIntHashSet subClasses = myClassToSubclasses.get(fromClass.name);
      if (subClasses == null) {
        return;
      }
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
      }
      if (!visitedClasses.add(fromClass.name)) {
        return;
      }
      final TIntHashSet _visitedClasses = visitedClasses;
      subClasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int subClassName) {
          final ClassRepr r = reprByName(subClassName);

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
          return true;
        }
      });
    }

    private Collection<Pair<MethodRepr, ClassRepr>> findAllMethodsBySpecificity(final MethodRepr m, final ClassRepr c) {
      final MethodRepr.Predicate predicate = lessSpecific(m);
      final Collection<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      addOverridenMethods(c, predicate, result, null);
      addOverridingMethods(m, c, predicate, result, null);
      return result;
    }

    private Collection<Pair<MethodRepr, ClassRepr>> findOverriddenMethods(final MethodRepr m, final ClassRepr c) {
      if (m.name == myInitName) {
        return Collections.emptySet(); // overriding is not defined for constructors
      }
      final Collection<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      addOverridenMethods(c, MethodRepr.equalByJavaRules(m), result, null);
      return result;
    }

    private boolean hasOverriddenMethods(final ClassRepr fromClass, final MethodRepr.Predicate predicate, TIntHashSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (int superName : fromClass.getSupers()) {
        if (!visitedClasses.add(superName) || superName == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = reprByName(superName);
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

    private boolean extendsLibraryClass(final ClassRepr fromClass, TIntHashSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (int superName : fromClass.getSupers()) {
        if (!visitedClasses.add(superName) || superName == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = reprByName(superName);
        if (superClass == null || extendsLibraryClass(superClass, visitedClasses)) {
          return true;
        }
      }
      return false;
    }

    private void addOverridenMethods(final ClassRepr fromClass, final MethodRepr.Predicate predicate, final Collection<Pair<MethodRepr, ClassRepr>> container, TIntHashSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (int superName : fromClass.getSupers()) {
        if (!visitedClasses.add(superName)) {
          continue;  // prevent SOE
        }
        final ClassRepr superClass = reprByName(superName);
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

    void addOverriddenFields(final FieldRepr f, final ClassRepr fromClass, final Collection<Pair<FieldRepr, ClassRepr>> container, TIntHashSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (int supername : fromClass.getSupers()) {
        if (!visitedClasses.add(supername) || supername == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = reprByName(supername);
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

    boolean hasOverriddenFields(final FieldRepr f, final ClassRepr fromClass, TIntHashSet visitedClasses) {
      if (visitedClasses == null) {
        visitedClasses = new TIntHashSet();
        visitedClasses.add(fromClass.name);
      }
      for (int supername : fromClass.getSupers()) {
        if (!visitedClasses.add(supername) || supername == myObjectClassName) {
          continue;
        }
        final ClassRepr superClass = reprByName(supername);
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

    @Nullable
    ClassRepr reprByName(final int name) {
      if (myMappings != null) {
        final ClassRepr r = myMappings.getReprByName(null, name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(null, name);
    }

    @Nullable
    private Boolean isInheritorOf(final int who, final int whom, TIntHashSet visitedClasses) {
      if (who == whom) {
        return Boolean.TRUE;
      }

      final ClassRepr repr = reprByName(who);

      if (repr != null) {
        if (visitedClasses == null) {
          visitedClasses = new TIntHashSet();
          visitedClasses.add(who);
        }
        for (int s : repr.getSupers()) {
          if (!visitedClasses.add(s)) {
            continue;
          }
          final Boolean inheritorOf = isInheritorOf(s, whom, visitedClasses);
          if (inheritorOf != null && inheritorOf) {
            return inheritorOf;
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
      final ClassRepr r = reprByName(className);
      if (r == null || r.getFields().contains(field)) {
        return true;
      }
      return hasOverriddenFields(field, r, null);
    }

    void collectSupersRecursively(final int className, @NotNull final TIntHashSet container) {
      final ClassRepr classRepr = reprByName(className);
      if (classRepr != null) {
        final int[] supers = classRepr.getSupers();
        if (container.addAll(supers)) {
          for (int aSuper : supers) {
            collectSupersRecursively(aSuper, container);
          }
        }
      }
    }

    void affectSubclasses(final int className, final Collection<File> affectedFiles, final Collection<UsageRepr.Usage> affectedUsages, final TIntHashSet dependants, final boolean usages, final Collection<File> alreadyCompiledFiles, TIntHashSet visitedClasses) {
      debug("Affecting subclasses of class: ", className);

      final Collection<File> allSources = myClassToSourceFile.get(className);
      if (allSources == null || allSources.isEmpty()) {
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

        final ClassRepr classRepr = reprByName(className);
        if (classRepr != null) {
          debug("Added class usage for ", classRepr.name);
          affectedUsages.add(classRepr.createUsage());
        }
      }

      final TIntHashSet depClasses = myClassToClassDependency.get(className);
      if (depClasses != null) {
        addAll(dependants, depClasses);
      }

      final TIntHashSet directSubclasses = myClassToSubclasses.get(className);
      if (directSubclasses != null) {
        if (visitedClasses == null) {
          visitedClasses = new TIntHashSet();
          visitedClasses.add(className);
        }
        final TIntHashSet _visitedClasses = visitedClasses;
        directSubclasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int subClass) {
            if (_visitedClasses.add(subClass)) {
              affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages, alreadyCompiledFiles, _visitedClasses);
            }
            return true;
          }
        });
      }
    }

    void affectFieldUsages(final FieldRepr field, final TIntHashSet classes, final UsageRepr.Usage rootUsage, final Set<UsageRepr.Usage> affectedUsages, final TIntHashSet dependents) {
      affectedUsages.add(rootUsage);

      classes.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int p) {
          final TIntHashSet deps = myClassToClassDependency.get(p);
          if (deps != null) {
            addAll(dependents, deps);
          }
          debug("Affect field usage referenced of class ", p);
          affectedUsages.add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(myContext, p) : field.createUsage(myContext, p));
          return true;
        }
      });
    }

    void affectMethodUsages(final MethodRepr method, final TIntHashSet subclasses, final UsageRepr.Usage rootUsage, final Set<UsageRepr.Usage> affectedUsages, final TIntHashSet dependents) {
      affectedUsages.add(rootUsage);
      if (subclasses != null) {
        subclasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int p) {
            final TIntHashSet deps = myClassToClassDependency.get(p);
            if (deps != null) {
              addAll(dependents, deps);
            }

            debug("Affect method usage referenced of class ", p);

            final UsageRepr.Usage usage =
              rootUsage instanceof UsageRepr.MetaMethodUsage ? method.createMetaUsage(myContext, p) : method.createUsage(myContext, p);
            affectedUsages.add(usage);
            return true;
          }
        });
      }
    }

    public abstract class UsageConstraint {
      public abstract boolean checkResidence(final int residence);
    }

    public class FileFilterConstraint extends UsageConstraint {
      @NotNull
      private final DependentFilesFilter myFilter;

      public FileFilterConstraint(@NotNull DependentFilesFilter filter) {
        myFilter = filter;
      }

      public boolean checkResidence(int residence) {
        final Collection<File> fNames = myClassToSourceFile.get(residence);
        if (fNames == null || fNames.isEmpty()) {
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

    public class PackageConstraint extends UsageConstraint {
      public final String packageName;

      public PackageConstraint(final String packageName) {
        this.packageName = packageName;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return !ClassRepr.getPackageName(myContext.getValue(residence)).equals(packageName);
      }
    }

    public class InheritanceConstraint extends PackageConstraint {
      public final int rootClass;

      public InheritanceConstraint(final int rootClass) {
        super(ClassRepr.getPackageName(myContext.getValue(rootClass)));
        this.rootClass = rootClass;
      }

      @Override
      public boolean checkResidence(final int residence) {
        final Boolean inheritorOf = isInheritorOf(residence, rootClass, null);
        return inheritorOf == null || !inheritorOf || super.checkResidence(residence);
      }
    }

    public class NegationConstraint extends UsageConstraint {
      final UsageConstraint x;

      public NegationConstraint(UsageConstraint x) {
        this.x = x;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return !x.checkResidence(residence);
      }
    }

    public class IntersectionConstraint extends UsageConstraint {
      final UsageConstraint x;
      final UsageConstraint y;

      public IntersectionConstraint(final UsageConstraint x, final UsageConstraint y) {
        this.x = x;
        this.y = y;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return x.checkResidence(residence) && y.checkResidence(residence);
      }
    }
  }

  void affectAll(final int className, @NotNull final File sourceFile, final Collection<File> affectedFiles, final Collection<File> alreadyCompiledFiles, @Nullable final DependentFilesFilter filter) {
    final TIntHashSet dependants = myClassToClassDependency.get(className);
    if (dependants != null) {
      dependants.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int depClass) {
          final Collection<File> allSources = myClassToSourceFile.get(depClass);
          if (allSources == null || allSources.isEmpty()) {
            return true;
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

          return true;
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
  private TIntHashSet getAllSubclasses(final int root) {
    return addAllSubclasses(root, new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR));
  }

  private TIntHashSet addAllSubclasses(final int root, final TIntHashSet acc) {
    if (!acc.add(root)) {
      return acc;
    }
    final TIntHashSet directSubclasses = myClassToSubclasses.get(root);
    if (directSubclasses != null) {
      directSubclasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int s) {
          addAllSubclasses(s, acc);
          return true;
        }
      });
    }
    return acc;
  }

  private boolean incrementalDecision(final int owner, final Proto member, final Collection<File> affectedFiles, final Collection<File> currentlyCompiled, @Nullable final DependentFilesFilter filter) {
    final boolean isField = member instanceof FieldRepr;
    final Util self = new Util();

    // Public branch --- hopeless
    if (member.isPublic()) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    final THashSet<File> toRecompile = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

    // Protected branch
    if (member.isProtected()) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);

      final TIntHashSet propagated = self.propagateFieldAccess(isField ? member.name : myEmptyName, owner);
      propagated.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int className) {
          final Collection<File> fileNames = myClassToSourceFile.get(className);
          if (fileNames != null) {
            for (File fileName : fileNames) {
              debug("Adding ", fileName);
            }
            toRecompile.addAll(fileNames);
          }
          return true;
        }
      });
    }

    final String packageName = ClassRepr.getPackageName(myContext.getValue(isField ? owner : member.name));

    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);

    // Package-local branch
    myClassToSourceFile.forEachEntry(new TIntObjectProcedure<Collection<File>>() {
      @Override
      public boolean execute(int className, Collection<File> fileNames) {
        if (ClassRepr.getPackageName(myContext.getValue(className)).equals(packageName)) {
          for (File fileName : fileNames) {
            if (filter == null || filter.accept(fileName)) {
              debug("Adding: ", fileName);
              toRecompile.add(fileName);
            }
          }
        }
        return true;
      }
    });

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

  private class Differential {
    private static final int DESPERATE_MASK = Opcodes.ACC_FINAL;

    final Mappings myDelta;
    final Collection<File> myFilesToCompile;
    final Collection<File> myCompiledFiles;
    final Collection<File> myAffectedFiles;
    @Nullable
    final DependentFilesFilter myFilter;
    @Nullable final Callbacks.ConstantAffectionResolver myConstantSearch;
    final DelayedWorks myDelayedWorks;

    final Util myFuture;
    final Util myPresent;

    final boolean myEasyMode; // true means: no need to search for affected files, only preprocess data for integrate

    private class DelayedWorks {
      class Triple {
        final int owner;
        final FieldRepr field;
        @Nullable
        final Future<Callbacks.ConstantAffection> affection;

        private Triple(final int owner, final FieldRepr field, @Nullable final Future<Callbacks.ConstantAffection> affection) {
          this.owner = owner;
          this.field = field;
          this.affection = affection;
        }

        Callbacks.ConstantAffection getAffection() {
          try {
            return affection != null ? affection.get() : Callbacks.ConstantAffection.EMPTY;
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }

      final Collection<Triple> myQueue = new LinkedList<Triple>();

      void addConstantWork(final int ownerClass, final FieldRepr changedField, final boolean isRemoved, boolean accessChanged) {
        final Future<Callbacks.ConstantAffection> future;
        if (myConstantSearch == null) {
          future = null;
        }
        else {
          final String className = myContext.getValue(ownerClass);
          final String fieldName = myContext.getValue(changedField.name);
          future = myConstantSearch.request(className.replace('/', '.'), fieldName, changedField.access, isRemoved, accessChanged);
        }
        myQueue.add(new Triple(ownerClass, changedField, future));
      }

      boolean doWork(@NotNull final Collection<File> affectedFiles) {
        if (!myQueue.isEmpty()) {
          debug("Starting delayed works.");

          for (final Triple t : myQueue) {
            final Callbacks.ConstantAffection affection = t.getAffection();

            debug("Class: ", t.owner);
            debug("Field: ", t.field.name);

            if (!affection.isKnown()) {
              if (myConstantSearch != null) {
                debug("No external dependency information available.");
              }
              else {
                debug("Constant search service not available.");
              }
              debug("Trying to soften non-incremental decision.");
              if (!incrementalDecision(t.owner, t.field, affectedFiles, myFilesToCompile, myFilter)) {
                debug("No luck.");
                debug("End of delayed work, returning false.");
                return false;
              }
            }
            else {
              debug("External dependency information retrieved.");
              final Collection<File> files = affection.getAffectedFiles();
              if (myFilter == null) {
                affectedFiles.addAll(files);
              }
              else {
                for (File file : files) {
                  if (myFilter.accept(file)) {
                    affectedFiles.add(file);
                  }
                }
              }
            }
          }

          debug("End of delayed work, returning true.");
        }
        return true;
      }
    }

    private class FileClasses {
      final File myFileName;
      final Set<ClassRepr> myFileClasses;

      FileClasses(File fileName, Collection<ClassRepr> fileClasses) {
        this.myFileName = fileName;
        this.myFileClasses = new HashSet<ClassRepr>(fileClasses);
      }
    }

    private class DiffState {
      final public TIntHashSet myDependants = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

      final public Set<UsageRepr.Usage> myAffectedUsages = new HashSet<UsageRepr.Usage>();
      final public Set<UsageRepr.AnnotationUsage> myAnnotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
      final public Map<UsageRepr.Usage, Util.UsageConstraint> myUsageConstraints = new HashMap<UsageRepr.Usage, Util.UsageConstraint>();

      final Difference.Specifier<ClassRepr> myClassDiff;

      private DiffState(Difference.Specifier<ClassRepr> classDiff) {
        this.myClassDiff = classDiff;
      }
    }

    private Differential(final Mappings delta) {
      this.myDelta = delta;
      this.myFilesToCompile = null;
      this.myCompiledFiles = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      this.myConstantSearch = null;

      myDelayedWorks = null;

      myFuture = null;
      myPresent = null;

      myEasyMode = true;

      delta.myIsRebuild = true;
    }

    private Differential(final Mappings delta, final Collection<String> removed, final Collection<File> filesToCompile) {
      delta.myRemovedFiles = removed;

      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      this.myConstantSearch = null;

      myDelayedWorks = null;

      myFuture = new Util(delta);
      myPresent = new Util();
      myEasyMode = true;
    }

    private Differential(final Mappings delta,
                         final Collection<String> removed,
                         final Collection<File> filesToCompile,
                         final Collection<File> compiledFiles,
                         final Collection<File> affectedFiles,
                         @NotNull final DependentFilesFilter filter,
                         @Nullable final Callbacks.ConstantAffectionResolver constantSearch) {
      delta.myRemovedFiles = removed;

      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = compiledFiles;
      this.myAffectedFiles = affectedFiles;
      this.myFilter = filter;
      this.myConstantSearch = constantSearch;

      myDelayedWorks = new DelayedWorks();

      myFuture = new Util(delta);
      myPresent = new Util();

      myEasyMode = false;
    }

    private void processDisappearedClasses() {
      myDelta.compensateRemovedContent(myFilesToCompile);

      if (!myEasyMode) {
        final Collection<String> removed = myDelta.myRemovedFiles;

        if (removed != null) {
          for (final String file : removed) {
            final File sourceFile = new File(file);
            final Collection<ClassRepr> classes = mySourceFileToClasses.get(sourceFile);

            if (classes != null) {
              for (ClassRepr c : classes) {
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

      Ref<ClassRepr> oldItRef = null;
      for (final MethodRepr m : added) {
        debug("Method: ", m.name);
        if (it.isInterface() || it.isAbstract() || m.isAbstract()) {
          debug("Class is abstract, or is interface, or added method in abstract => affecting all subclasses");
          myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
        }

        TIntHashSet propagated = null;

        if (!m.isPrivate() && m.name != myInitName) {
          if (oldItRef == null) {
            oldItRef = new Ref<ClassRepr>(getReprByName(null, it.name)); // lazy init
          }
          final ClassRepr oldIt = oldItRef.get();

          if (oldIt == null || !myPresent.hasOverriddenMethods(oldIt, MethodRepr.equalByJavaRules(m), null)) {
            if (m.myArgumentTypes.length > 0) {
              propagated = myFuture.propagateMethodAccess(m, it.name);
              debug("Conservative case on overriding methods, affecting method usages");
              myFuture.affectMethodUsages(m, propagated, m.createMetaUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
            }
          }
        }

        if (!m.isPrivate()) {
          final Collection<Pair<MethodRepr, ClassRepr>> affectedMethods = myFuture.findAllMethodsBySpecificity(m, it);
          final MethodRepr.Predicate overrides = MethodRepr.equalByJavaRules(m);

          if (propagated == null) {
            propagated = myFuture.propagateMethodAccess(m, it.name);
          }

          final Collection<MethodRepr> lessSpecific = it.findMethods(myFuture.lessSpecific(m));

          for (final MethodRepr mm : lessSpecific) {
            if (!mm.equals(m)) {
              debug("Found less specific method, affecting method usages");
              myFuture.affectMethodUsages(mm, propagated, mm.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
            }
          }

          debug("Processing affected by specificity methods");
          for (final Pair<MethodRepr, ClassRepr> pair : affectedMethods) {
            final MethodRepr method = pair.first;
            final ClassRepr methodClass = pair.second;

            if (methodClass == MOCK_CLASS) {
              continue;
            }
            final Boolean inheritorOf = myPresent.isInheritorOf(methodClass.name, it.name, null);
            final boolean isInheritor = inheritorOf != null && inheritorOf;

            debug("Method: ", method.name);
            debug("Class : ", methodClass.name);

            if (overrides.satisfy(method) && isInheritor) {
              debug("Current method overrides that found");

              final Collection<File> files = myClassToSourceFile.get(methodClass.name);
              if (files != null) {
                myAffectedFiles.addAll(files);
                for (File file : files) {
                  debug("Affecting file ", file);
                }
              }

            }
            else {
              debug("Current method does not override that found");

              final TIntHashSet yetPropagated = myPresent.propagateMethodAccess(method, it.name);

              if (isInheritor) {
                final TIntHashSet deps = myClassToClassDependency.get(methodClass.name);

                if (deps != null) {
                  addAll(state.myDependants, deps);
                }

                myFuture.affectMethodUsages(method, yetPropagated, method.createUsage(myContext, methodClass.name), state.myAffectedUsages,
                                             state.myDependants);
              }

              debug("Affecting method usages for that found");
              myFuture.affectMethodUsages(method, yetPropagated, method.createUsage(myContext, it.name), state.myAffectedUsages,
                                           state.myDependants);
            }
          }

          final TIntHashSet subClasses = getAllSubclasses(it.name);
          subClasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subClass) {
              final ClassRepr r = myFuture.reprByName(subClass);
              if (r == null) {
                return true;
              }
              final Collection<File> sourceFileNames = myClassToSourceFile.get(subClass);
              if (sourceFileNames != null && !myCompiledFiles.containsAll(sourceFileNames)) {
                final int outerClass = r.getOuterClassName();
                if (!isEmpty(outerClass)) {
                  final ClassRepr outerClassRepr = myFuture.reprByName(outerClass);
                  if (outerClassRepr != null && (myFuture.isMethodVisible(outerClassRepr, m) || myFuture.extendsLibraryClass(outerClassRepr, null))) {
                    myAffectedFiles.addAll(sourceFileNames);
                    for (File sourceFileName : sourceFileNames) {
                      debug("Affecting file due to local overriding: ", sourceFileName);
                    }
                  }
                }
              }
              return true;
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
      assert myAffectedFiles != null;
      assert myCompiledFiles != null;

      debug("Processing removed methods:");
      for (final MethodRepr m : removed) {
        debug("Method ", m.name);

        final Collection<Pair<MethodRepr, ClassRepr>> overridenMethods = myFuture.findOverriddenMethods(m, it);
        final TIntHashSet propagated = myFuture.propagateMethodAccess(m, it.name);

        if (overridenMethods.size() == 0) {
          debug("No overridden methods found, affecting method usages");
          myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
        }
        else {
          boolean clear = true;

          loop:
          for (final Pair<MethodRepr, ClassRepr> overriden : overridenMethods) {
            final MethodRepr mm = overriden.first;

            if (mm == MOCK_METHOD || !mm.myType.equals(m.myType) || !isEmpty(mm.signature) || !isEmpty(m.signature) || m.isMoreAccessibleThan(mm)) {
              clear = false;
              break loop;
            }
          }

          if (!clear) {
            debug("No clearly overridden methods found, affecting method usages");
            myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
          }
        }

        final Collection<Pair<MethodRepr, ClassRepr>> overridingMethods = new HashSet<Pair<MethodRepr, ClassRepr>>();

        myFuture.addOverridingMethods(m, it, MethodRepr.equalByJavaRules(m), overridingMethods, null);

        for (final Pair<MethodRepr, ClassRepr> p : overridingMethods) {
          final Collection<File> fNames = myClassToSourceFile.get(p.second.name);
          if (fNames != null) {
            myAffectedFiles.addAll(fNames);
            for (File fName : fNames) {
              debug("Affecting file by overriding: ", fName);
            }
          }
        }

        if (!m.isAbstract()) {
          propagated.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int p) {
              if (p != it.name) {
                final ClassRepr s = myFuture.reprByName(p);

                if (s != null) {
                  final Collection<Pair<MethodRepr, ClassRepr>> overridenInS = myFuture.findOverriddenMethods(m, s);

                  overridenInS.addAll(overridenMethods);

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
                    final Collection<File> sources = myClassToSourceFile.get(p);

                    if (sources != null && !myCompiledFiles.containsAll(sources)) {
                      myAffectedFiles.addAll(sources);
                      debug("Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass ", p);
                      for (File source : sources) {
                        debug("Affecting subclass source file ", source);
                      }
                    }
                  }
                }
              }
              return true;
            }
          });
        }
      }
      debug("End of removed methods processing");
    }

    private void processChangedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      final Collection<Pair<MethodRepr, Difference>> changed = diff.methods().changed();
      if (changed.isEmpty()) {
        return;
      }
      debug("Processing changed methods:");

      assert myFuture != null;
      assert myAffectedFiles != null;

      for (final Pair<MethodRepr, Difference> mr : changed) {
        final MethodRepr m = mr.first;
        final MethodRepr.Diff d = (MethodRepr.Diff)mr.second;
        final boolean throwsChanged = !d.exceptions().unchanged();

        debug("Method: ", m.name);

        if (it.isAnnotation()) {
          if (d.defaultRemoved()) {
            debug("Class is annotation, default value is removed => adding annotation query");
            final TIntHashSet l = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
            l.add(m.name);
            final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)UsageRepr
              .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), l, null);
            state.myAnnotationQuery.add(annotationUsage);
          }
        }
        else if (d.base() != Difference.NONE || throwsChanged) {
          final TIntHashSet propagated = myFuture.propagateMethodAccess(m, it.name);

          boolean affected = false;
          boolean constrained = false;

          final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

          if (d.packageLocalOn()) {
            debug("Method became package-local, affecting method usages outside the package");
            myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, state.myDependants);

            for (final UsageRepr.Usage usage : usages) {
              state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(it.name));
            }

            state.myAffectedUsages.addAll(usages);
            affected = true;
            constrained = true;
          }

          if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
            if (!affected) {
              debug("Return type, throws list or signature changed --- affecting method usages");
              myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, state.myDependants);

              final List<Pair<MethodRepr, ClassRepr>> overridingMethods = new LinkedList<Pair<MethodRepr, ClassRepr>>();

              myFuture.addOverridingMethods(m, it, MethodRepr.equalByJavaRules(m), overridingMethods, null);

              for(final Pair<MethodRepr, ClassRepr> p : overridingMethods) {
                final ClassRepr aClass = p.getSecond();

                if (aClass != MOCK_CLASS) {
                  final Collection<File> fileNames = myClassToSourceFile.get(aClass.name);
                  if (fileNames != null) {
                    myAffectedFiles.addAll(fileNames);
                  }
                }
              }

              state.myAffectedUsages.addAll(usages);
            }
          }
          else if ((d.base() & Difference.ACCESS) > 0) {
            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
              if (!affected) {
                debug("Added static or private specifier or removed static specifier --- affecting method usages");
                myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
              }

              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0) {
                debug("Added static specifier --- affecting subclasses");
                myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
              }
            }
            else {
              if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PUBLIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_ABSTRACT) > 0) {
                debug("Added final, public or abstract specifier --- affecting subclasses");
                myFuture.affectSubclasses(it.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, false, myCompiledFiles, null);
              }

              if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && !((d.removedModifiers() & Opcodes.ACC_PRIVATE) > 0)) {
                if (!constrained) {
                  debug("Added public or package-local method became protected --- affect method usages with protected constraint");
                  if (!affected) {
                    myFuture.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, state.myDependants);
                    state.myAffectedUsages.addAll(usages);
                  }

                  for (final UsageRepr.Usage usage : usages) {
                    state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(it.name));
                  }
                }
              }
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
          final TIntHashSet subClasses = getAllSubclasses(classRepr.name);
          subClasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subClass) {
              final ClassRepr r = myFuture.reprByName(subClass);
              if (r != null) {
                final Collection<File> sourceFileNames = myClassToSourceFile.get(subClass);
                if (sourceFileNames != null && !myCompiledFiles.containsAll(sourceFileNames)) {
                  if (r.isLocal()) {
                    for (File sourceFileName : sourceFileNames) {
                      debug("Affecting local subclass (introduced field can potentially hide surrounding method parameters/local variables): ", sourceFileName);
                    }
                    myAffectedFiles.addAll(sourceFileNames);
                  }
                  else {
                    final int outerClass = r.getOuterClassName();
                    if (!isEmpty(outerClass) && myFuture.isFieldVisible(outerClass, f)) {
                      for (File sourceFileName : sourceFileNames) {
                        debug("Affecting inner subclass (introduced field can potentially hide surrounding class fields): ", sourceFileName);
                      }
                      myAffectedFiles.addAll(sourceFileNames);
                    }
                  }
                }
              }

              debug("Affecting field usages referenced from subclass ", subClass);
              final TIntHashSet propagated = myFuture.propagateFieldAccess(f.name, subClass);
              myFuture.affectFieldUsages(f, propagated, f.createUsage(myContext, subClass), state.myAffectedUsages, state.myDependants);

              final TIntHashSet deps = myClassToClassDependency.get(subClass);

              if (deps != null) {
                addAll(state.myDependants, deps);
              }
              return true;
            }
          });
        }

        final Collection<Pair<FieldRepr, ClassRepr>> overriddenFields = new HashSet<Pair<FieldRepr, ClassRepr>>();
        myFuture.addOverriddenFields(f, classRepr, overriddenFields, null);

        for (final Pair<FieldRepr, ClassRepr> p : overriddenFields) {
          final FieldRepr ff = p.first;
          final ClassRepr cc = p.second;

          if (!ff.isPrivate()) {
            final TIntHashSet propagated = myPresent.propagateFieldAccess(ff.name, cc.name);
            final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

            debug("Affecting usages of overridden field in class ", cc.name);
            myFuture.affectFieldUsages(ff, propagated, ff.createUsage(myContext, cc.name), localUsages, state.myDependants);

            if (f.isPrivate() || (f.isPublic() && (ff.isPublic() || ff.isPackageLocal())) || (f.isProtected() && ff.isProtected()) || (f.isPackageLocal() && ff.isPackageLocal())) {
              // nothing
            }
            else {
              Util.UsageConstraint constraint;

              if ((ff.isProtected() && f.isPublic()) || (f.isProtected() && ff.isPublic()) || (ff.isPackageLocal() && f.isProtected())) {
                constraint = myFuture.new NegationConstraint(myFuture.new InheritanceConstraint(cc.name));
              }
              else if (ff.isPublic() && ff.isPackageLocal()) {
                constraint = myFuture.new NegationConstraint(myFuture.new PackageConstraint(cc.getPackageName()));
              }
              else {
                constraint =
                  myFuture.new IntersectionConstraint(myFuture.new NegationConstraint(myFuture.new InheritanceConstraint(cc.name)),
                                                       myFuture.new NegationConstraint(
                                                         myFuture.new PackageConstraint(cc.getPackageName())));
              }

              for (final UsageRepr.Usage usage : localUsages) {
                state.myUsageConstraints.put(usage, constraint);
              }
            }

            state.myAffectedUsages.addAll(localUsages);
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
      assert myFuture != null;

      debug("Processing removed fields:");

      for (final FieldRepr f : removed) {
        debug("Field: ", f.name);

        if (!f.isPrivate() && (f.access & DESPERATE_MASK) == DESPERATE_MASK && f.hasValue()) {
          debug("Field had value and was (non-private) final static => a switch to non-incremental mode requested");
          if (myConstantSearch != null) {
            assert myDelayedWorks != null;
            myDelayedWorks.addConstantWork(it.name, f, true, false);
          }
          else {
            if (!incrementalDecision(it.name, f, myAffectedFiles, myFilesToCompile, myFilter)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }
        }

        final TIntHashSet propagated = myFuture.propagateFieldAccess(f.name, it.name);
        myFuture.affectFieldUsages(f, propagated, f.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
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

      for (final Pair<FieldRepr, Difference> f : changed) {
        final Difference d = f.second;
        final FieldRepr field = f.first;

        debug("Field: ", field.name);
        
        // only if the field was a compile-time constant
        if (!field.isPrivate() && (field.access & DESPERATE_MASK) == DESPERATE_MASK && d.hadValue()) { 
          final int changedModifiers = d.addedModifiers() | d.removedModifiers();
          final boolean harmful = (changedModifiers & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) > 0;
          final boolean accessChanged = (changedModifiers & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) > 0;
          final boolean becameLessAccessible = accessChanged && !d.weakedAccess();
          final boolean valueChanged = (d.base() & Difference.VALUE) > 0;

          if (harmful || valueChanged || becameLessAccessible) {
            debug("Inline field changed it's access or value => a switch to non-incremental mode requested");
            if (myConstantSearch != null) {
              assert myDelayedWorks != null;
              myDelayedWorks.addConstantWork(it.name, field, false, accessChanged);
            }
            else {
              if (!incrementalDecision(it.name, field, myAffectedFiles, myFilesToCompile, myFilter)) {
                debug("End of Differentiate, returning false");
                return false;
              }
            }
          }
        }

        if (d.base() != Difference.NONE) {
          final TIntHashSet propagated = myFuture.propagateFieldAccess(field.name, it.name);

          if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
            debug("Type or signature changed --- affecting field usages");
            myFuture
              .affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
          }
          else if ((d.base() & Difference.ACCESS) > 0) {
            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
              debug("Added/removed static modifier or added private/volatile modifier --- affecting field usages");
              myFuture
                .affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), state.myAffectedUsages, state.myDependants);
            }
            else {
              boolean affected = false;
              final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

              if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                debug("Added final modifier --- affecting field assign usages");
                myFuture.affectFieldUsages(field, propagated, field.createAssignUsage(myContext, it.name), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
                affected = true;
              }

              if ((d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                debug("Removed public modifier, affecting field usages with appropriate constraint");
                if (!affected) {
                  myFuture.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), usages, state.myDependants);
                  state.myAffectedUsages.addAll(usages);
                }

                for (final UsageRepr.Usage usage : usages) {
                  if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0) {
                    state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(it.name));
                  }
                  else {
                    state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(it.getPackageName()));
                  }
                }
              }
            }
          }
        }
      }
      debug("End of changed fields processing");

      return true;
    }

    private boolean processChangedClasses(final DiffState state) {
      final Collection<Pair<ClassRepr, Difference>> changedClasses = state.myClassDiff.changed();
      if (!changedClasses.isEmpty()) {
        debug("Processing changed classes:");
        assert myFuture != null;
        assert myPresent != null;

        final Util.FileFilterConstraint fileFilterConstraint = myFilter != null? myPresent.new FileFilterConstraint(myFilter) : null;

        for (final Pair<ClassRepr, Difference> changed : changedClasses) {
          final ClassRepr changedClass = changed.first;
          final ClassRepr.Diff diff = (ClassRepr.Diff)changed.second;

          myDelta.addChangedClass(changedClass.name);

          debug("Changed: ", changedClass.name);

          final int addedModifiers = diff.addedModifiers();

          final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) > 0;
          final boolean interfacesChanged = !diff.interfaces().unchanged();
          final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) > 0;

          if (superClassChanged) {
            myDelta.registerRemovedSuperClass(changedClass.name, changedClass.getSuperClass().className);

            final ClassRepr newClass = myDelta.getReprByName(null, changedClass.name);

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

          myPresent.appendDependents(changedClass, state.myDependants);

          if (superClassChanged || interfacesChanged || signatureChanged) {
            debug("Superclass changed: ", superClassChanged);
            debug("Interfaces changed: ", interfacesChanged);
            debug("Signature changed ", signatureChanged);

            final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
            final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

            debug("Extends changed: ", extendsChanged);
            debug("Interfaces removed: ", interfacesRemoved);

            myFuture.affectSubclasses(changedClass.name, myAffectedFiles, state.myAffectedUsages, state.myDependants, extendsChanged || interfacesRemoved || signatureChanged, myCompiledFiles, null);

            if (!changedClass.isAnonymous()) {
              final TIntHashSet parents = new TIntHashSet();
              myPresent.collectSupersRecursively(changedClass.name, parents);
              final TIntHashSet futureParents = new TIntHashSet();
              myFuture.collectSupersRecursively(changedClass.name, futureParents);
              parents.removeAll(futureParents.toArray());
              parents.remove(myObjectClassName);
              if (!parents.isEmpty()) {
                parents.forEach(new TIntProcedure() {
                  @Override
                  public boolean execute(int className) {
                    debug("Affecting usages in generic type parameter bounds of class: ", className);
                    final UsageRepr.Usage usage = UsageRepr.createClassAsGenericBoundUsage(myContext, className);
                    state.myAffectedUsages.add(usage);
                    if (fileFilterConstraint != null) {
                      state.myUsageConstraints.put(usage, fileFilterConstraint);
                    }

                    final TIntHashSet depClasses = myClassToClassDependency.get(className);
                    if (depClasses != null) {
                      addAll(state.myDependants, depClasses);
                    }
                    return true;
                  }
                });
              }
            }
          }

          if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) > 0 || (diff.removedModifiers() & Opcodes.ACC_INTERFACE) > 0) {
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

          if ((addedModifiers & Opcodes.ACC_PROTECTED) > 0) {
            debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
            final UsageRepr.Usage usage = changedClass.createUsage();

            state.myAffectedUsages.add(usage);
            state.myUsageConstraints.put(usage, myFuture.new InheritanceConstraint(changedClass.name));
          }

          if (diff.packageLocalOn()) {
            debug("Introduction of 'package-private' access detected, adding class usage + package constraint to affected usages");
            final UsageRepr.Usage usage = changedClass.createUsage();

            state.myAffectedUsages.add(usage);
            state.myUsageConstraints.put(usage, myFuture.new PackageConstraint(changedClass.getPackageName()));
          }

          if ((addedModifiers & Opcodes.ACC_FINAL) > 0 || (addedModifiers & Opcodes.ACC_PRIVATE) > 0) {
            debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
            state.myAffectedUsages.add(changedClass.createUsage());
          }

          if ((addedModifiers & Opcodes.ACC_ABSTRACT) > 0 || (addedModifiers & Opcodes.ACC_STATIC) > 0) {
            debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
            state.myAffectedUsages.add(UsageRepr.createClassNewUsage(myContext, changedClass.name));
          }

          if (changedClass.isAnnotation()) {
            debug("Class is annotation, performing annotation-specific analysis");

            if (diff.retentionChanged()) {
              debug("Retention policy change detected, adding class usage to affected usages");
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
                final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)UsageRepr
                  .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, changedClass.name), null, EnumSet.copyOf(removedtargets));
                state.myAnnotationQuery.add(annotationUsage);
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

    private void processAddedClasses(final DiffState state, File srcFile) {
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
            final Set<File> candidates = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
            final Collection<File> currentlyMapped = myClassToSourceFile.get(c.name);
            if (currentlyMapped != null) {
              candidates.addAll(currentlyMapped);
            }
            candidates.removeAll(myCompiledFiles);
            final Collection<File> newSources = myDelta.myClassToSourceFile.get(c.name);
            if (newSources != null) {
              candidates.removeAll(newSources);
            }
            final Set<File> nonExistentOrOutOfScope = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
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
                candidates.addAll(currentlyMapped);
              }
              if (newSources != null) {
                candidates.addAll(newSources);
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

        for (final int sup : c.getSupers()) {
          myDelta.registerAddedSuperClass(c.name, sup);
        }

        if (!myEasyMode && !c.isAnonymous() && !c.isLocal()) {
          final TIntHashSet toAffect = new TIntHashSet();
          toAffect.add(c.name);
          final TIntHashSet classes = myShortClassNameIndex.get(myContext.get(c.getShortName()));
          if (classes != null) {
            // affecting dependencies on all other classes with the same short name
            toAffect.addAll(classes.toArray());
          }
          toAffect.forEach(new TIntProcedure() {
            public boolean execute(int qName) {
              final TIntHashSet depClasses = myClassToClassDependency.get(qName);
              if (depClasses != null) {
                affectCorrespondingSourceFiles(depClasses);
              }
              return true;
            }
          });
        }
      }

      debug("End of added classes processing.");
    }

    private void affectCorrespondingSourceFiles(TIntHashSet toAffect) {
      assert myAffectedFiles != null;

      toAffect.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int depClass) {
          final Collection<File> fNames = myClassToSourceFile.get(depClass);
          if (fNames != null) {
            for (File fName : fNames) {
              if (myFilter == null || myFilter.accept(fName)) {
                debug("Adding dependent file ", fName);
                myAffectedFiles.add(fName);
              }
            }
          }
          return true;
        }
      });
    }

    private void calculateAffectedFiles(final DiffState state) {
      debug("Checking dependent classes:");
      assert myAffectedFiles != null;
      assert myCompiledFiles != null;

      state.myDependants.forEach(new TIntProcedure() {
        @Override
        public boolean execute(final int depClass) {
          final Collection<File> depFiles = myClassToSourceFile.get(depClass);
          if (depFiles != null) {
            for (File depFile : depFiles) {
              processDependentFile(depClass, depFile);
            }
          }
          return true;
        }

        private void processDependentFile(int depClass, @NotNull File depFile) {
          if (myAffectedFiles.contains(depFile) || myCompiledFiles.contains(depFile)) {
            return;
          }

          debug("Dependent class: ", depClass);

          final ClassRepr classRepr = getReprByName(depFile, depClass);
          if (classRepr == null) {
            return;
          }

          final Set<UsageRepr.Usage> depUsages = classRepr.getUsages();
          if (depUsages == null || depUsages.isEmpty()) {
            return;
          }

          for (UsageRepr.Usage usage : depUsages) {
            if (usage instanceof UsageRepr.AnnotationUsage) {
              for (final UsageRepr.AnnotationUsage query : state.myAnnotationQuery) {
                if (query.satisfies(usage)) {
                  debug("Added file due to annotation query");
                  myAffectedFiles.add(depFile);
                  return;
                }
              }
            }
            else if (state.myAffectedUsages.contains(usage)) {
              final Util.UsageConstraint constraint = state.myUsageConstraints.get(usage);
              if (constraint == null) {
                debug("Added file with no constraints");
                myAffectedFiles.add(depFile);
                return;
              }
              if (constraint.checkResidence(depClass)) {
                debug("Added file with satisfied constraint");
                myAffectedFiles.add(depFile);
                return;
              }
            }
          }
        }
      });
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

          final List<FileClasses> newClasses = new ArrayList<FileClasses>();
          myDelta.mySourceFileToClasses.forEachEntry(new TObjectObjectProcedure<File, Collection<ClassRepr>>() {
            @Override
            public boolean execute(File fileName, Collection<ClassRepr> classes) {
              if (myFilesToCompile == null || myFilesToCompile.contains(fileName)) {
                // Consider only files actually compiled in this round.
                // For other sources the list of classes taken from this map will be possibly incomplete.
                newClasses.add(new FileClasses(fileName, classes));
              }
              return true;
            }
          });

          for (final FileClasses compiledFile : newClasses) {
            final File fileName = compiledFile.myFileName;
            final Set<ClassRepr> classes = compiledFile.myFileClasses;
            final Set<ClassRepr> pastClasses = (Set<ClassRepr>)mySourceFileToClasses.get(fileName);
            final DiffState state = new DiffState(Difference.make(pastClasses, classes));
  
            if (!processChangedClasses(state)) {
              if (!myEasyMode) {
                // turning non-incremental
                return false;
              }
            }
  
            processRemovedClases(state, fileName);
            processAddedClasses(state, fileName);
  
            if (!myEasyMode) {
              calculateAffectedFiles(state);
            }
          }

          debug("End of Differentiate.");

          if (myEasyMode) {
            return false;
          }
          assert myAffectedFiles != null;
          assert myDelayedWorks != null;

          final Collection<String> removed = myDelta.myRemovedFiles;
          if (removed != null) {
            for (final String r : removed) {
              myAffectedFiles.remove(new File(r));
            }
          }
          return myDelayedWorks.doWork(myAffectedFiles);
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
  }

  public void differentiateOnRebuild(final Mappings delta) {
    new Differential(delta).differentiate();
  }

  public void differentiateOnNonIncrementalMake(final Mappings delta,
                                                final Collection<String> removed,
                                                final Collection<File> filesToCompile) {
    new Differential(delta, removed, filesToCompile).differentiate();
  }

  public boolean differentiateOnIncrementalMake
    (final Mappings delta,
     final Collection<String> removed,
     final Collection<File> filesToCompile,
     final Collection<File> compiledFiles,
     final Collection<File> affectedFiles,
     @NotNull final DependentFilesFilter filter,
     @Nullable final Callbacks.ConstantAffectionResolver constantSearch) {
    return new Differential(delta, removed, filesToCompile, compiledFiles, affectedFiles, filter, constantSearch).differentiate();
  }

  private void cleanupBackDependency(final int className,
                                     @Nullable Set<UsageRepr.Usage> usages,
                                     final IntIntMultiMaplet buffer) {
    if (usages == null) {
      final ClassRepr repr = getReprByName(null, className);

      if (repr != null) {
        usages = repr.getUsages();
      }
    }

    if (usages != null) {
      for (final UsageRepr.Usage u : usages) {
        buffer.put(u.getOwner(), className);
      }
    }
  }

  private void cleanupRemovedClass(final Mappings delta, @NotNull final ClassRepr cr, File sourceFile, final Set<UsageRepr.Usage> usages, final IntIntMultiMaplet dependenciesTrashBin) {
    final int className = cr.name;

    // it is safe to cleanup class information if it is mapped to non-existing files only
    final Collection<File> currentlyMapped = myClassToSourceFile.get(className);
    if (currentlyMapped == null || currentlyMapped.isEmpty()) {
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

    for (final int superSomething : cr.getSupers()) {
      delta.registerRemovedSuperClass(className, superSomething);
    }

    cleanupBackDependency(className, usages, dependenciesTrashBin);

    myClassToClassDependency.remove(className);
    myClassToSubclasses.remove(className);
    myClassToSourceFile.remove(className);
    if (!cr.isLocal() && !cr.isAnonymous()) {
      myShortClassNameIndex.removeFrom(myContext.get(cr.getShortName()), className);
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
            final Set<ClassRepr> fileClasses = (Set<ClassRepr>)mySourceFileToClasses.get(deletedFile);

            if (fileClasses != null) {
              for (final ClassRepr aClass : fileClasses) {
                cleanupRemovedClass(delta, aClass, deletedFile, aClass.getUsages(), dependenciesTrashBin);
              }
              mySourceFileToClasses.remove(deletedFile);
            }
          }
        }

        if (!delta.isRebuild()) {
          for (final Pair<ClassRepr, File> pair : delta.getDeletedClasses()) {
            final ClassRepr deletedClass = pair.first;
            cleanupRemovedClass(delta, deletedClass, pair.second, deletedClass.getUsages(), dependenciesTrashBin);
          }
          for (ClassRepr repr : delta.getAddedClasses()) {
            if (!repr.isAnonymous() && !repr.isLocal()) {
              myShortClassNameIndex.put(myContext.get(repr.getShortName()), repr.name);
            }
          }

          final TIntHashSet superClasses = new TIntHashSet();
          final IntIntTransientMultiMaplet addedSuperClasses = delta.getAddedSuperClasses();
          final IntIntTransientMultiMaplet removedSuperClasses = delta.getRemovedSuperClasses();

          addAllKeys(superClasses, addedSuperClasses);
          addAllKeys(superClasses, removedSuperClasses);

          superClasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int superClass) {
              final TIntHashSet added = addedSuperClasses.get(superClass);
              TIntHashSet removed = removedSuperClasses.get(superClass);

              final TIntHashSet old = myClassToSubclasses.get(superClass);

              if (old == null) {
                if (added != null && !added.isEmpty()) {
                  myClassToSubclasses.replace(superClass, added);
                }
              }
              else {
                boolean changed = false;
                final int[] addedAsArray = added != null && !added.isEmpty()? added.toArray() : null;
                if (removed != null && !removed.isEmpty()) {
                  if (addedAsArray != null) {
                    // optimization: avoid unnecessary changes in the set
                    removed = (TIntHashSet)removed.clone();
                    removed.removeAll(addedAsArray);
                  }
                  if (!removed.isEmpty()) {
                    changed = old.removeAll(removed.toArray());
                  }
                }

                if (addedAsArray != null) {
                  changed |= old.addAll(addedAsArray);
                }

                if (changed) {
                  myClassToSubclasses.replace(superClass, old);
                }
              }

              return true;
            }
          });

          delta.getChangedClasses().forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int className) {
              final Collection<File> sourceFiles = delta.myClassToSourceFile.get(className);
              myClassToSourceFile.replace(className, sourceFiles);

              cleanupBackDependency(className, null, dependenciesTrashBin);

              return true;
            }
          });

          delta.getChangedFiles().forEach(new TObjectProcedure<File>() {
            @Override
            public boolean execute(final File fileName) {
              final Collection<ClassRepr> classes = delta.mySourceFileToClasses.get(fileName);
              mySourceFileToClasses.replace(fileName, classes);
              return true;
            }
          });
          
          // some classes may be associated with multiple sources.
          // In case some of these sources was not compiled, but the class was changed, we need to update
          // sourceToClasses mapping for such sources to include the updated ClassRepr version of the changed class
          final THashSet<File> unchangedSources = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
          delta.mySourceFileToClasses.forEachEntry(new TObjectObjectProcedure<File, Collection<ClassRepr>>() {
            @Override
            public boolean execute(File source, Collection<ClassRepr> b) {
              unchangedSources.add(source);
              return true;
            }
          });
          unchangedSources.removeAll(delta.getChangedFiles());
          if (!unchangedSources.isEmpty()) {
            unchangedSources.forEach(new TObjectProcedure<File>() {
              @Override
              public boolean execute(File unchangedSource) {
                final Collection<ClassRepr> updatedClasses = delta.mySourceFileToClasses.get(unchangedSource);
                if (updatedClasses != null && !updatedClasses.isEmpty()) {
                  final List<ClassRepr> classesToPut = new ArrayList<ClassRepr>();
                  final TIntHashSet updatedClassNames = new TIntHashSet();
                  for (ClassRepr aClass : updatedClasses) {
                    // from all generated classes on this round consider only 'differentiated' ones, for
                    // which we can reliably say that the class has changed. Keep classes, for which no such checks were made,
                    // to make it possible to create a diff and compare changes on next compilation rounds. 
                    if (delta.getChangedClasses().contains(aClass.name)) {
                      classesToPut.add(aClass);
                      updatedClassNames.add(aClass.name);
                    }
                  }
                  Collection<ClassRepr> currentClasses = mySourceFileToClasses.get(unchangedSource);
                  if (currentClasses != null) {
                    for (ClassRepr aClass : currentClasses) {
                      if (!updatedClassNames.contains(aClass.name)) {
                        classesToPut.add(aClass);
                      }
                    }
                  }
                  mySourceFileToClasses.replace(unchangedSource, classesToPut);
                }
                return true;
              }
            });
          }
        }
        else {
          myClassToSubclasses.putAll(delta.myClassToSubclasses);
          myClassToSourceFile.replaceAll(delta.myClassToSourceFile);
          mySourceFileToClasses.replaceAll(delta.mySourceFileToClasses);
          delta.mySourceFileToClasses.forEachEntry(new TObjectObjectProcedure<File, Collection<ClassRepr>>() {
            public boolean execute(File src, Collection<ClassRepr> classes) {
              for (ClassRepr repr : classes) {
                if (!repr.isAnonymous() && !repr.isLocal()) {
                  myShortClassNameIndex.put(myContext.get(repr.getShortName()), repr.name);
                }
              }
              return true;
            }
          });
        }

        // updating classToClass dependencies

        final TIntHashSet affectedClasses = new TIntHashSet();

        addAllKeys(affectedClasses, dependenciesTrashBin);
        addAllKeys(affectedClasses, delta.myClassToClassDependency);

        affectedClasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int aClass) {
            final TIntHashSet now = delta.myClassToClassDependency.get(aClass);
            final TIntHashSet toRemove = dependenciesTrashBin.get(aClass);
            final boolean hasDataToAdd = now != null && !now.isEmpty();

            if (toRemove != null && !toRemove.isEmpty()) {
              final TIntHashSet current = myClassToClassDependency.get(aClass);
              if (current != null && !current.isEmpty()) {
                final TIntHashSet before = new TIntHashSet();
                addAll(before, current);

                final boolean removed = current.removeAll(toRemove.toArray());
                final boolean added = hasDataToAdd && current.addAll(now.toArray());

                if ((removed && !added) || (!removed && added) || !before.equals(current)) {
                  myClassToClassDependency.replace(aClass, current);
                }
              }
              else {
                if (hasDataToAdd) {
                  myClassToClassDependency.put(aClass, now);
                }
              }
            }
            else {
              // nothing to remove for this class
              if (hasDataToAdd) {
                myClassToClassDependency.put(aClass, now);
              }
            }
            return true;
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

      public void associate(String classFileName, Collection<String> sources, ClassReader cr) {
        synchronized (myLock) {
          final int classFileNameS = myContext.get(classFileName);
          final Pair<ClassRepr, Set<UsageRepr.Usage>> result = new ClassfileAnalyzer(myContext).analyze(classFileNameS, cr);
          final ClassRepr repr = result.first;
          if (repr != null) {
            final Set<UsageRepr.Usage> localUsages = result.second;
            final int className = repr.name;

            for (String sourceFileName : sources) {
              final File sourceFile = new File(sourceFileName);
              myClassToSourceFile.put(className, sourceFile);
              mySourceFileToClasses.put(sourceFile, repr);
            }

            for (final int s : repr.getSupers()) {
              myClassToSubclasses.put(s, className);
            }

            for (final UsageRepr.Usage u : localUsages) {
              final int owner = u.getOwner();

              if (owner != className) {
                myClassToClassDependency.put(owner, className);
              }
            }
          }
        }
      }

      public void associate(final String classFileName, final String sourceFileName, final ClassReader cr) {
        associate(classFileName, Collections.singleton(sourceFileName), cr);
      }

      @Override
      public void registerImports(final String className, final Collection<String> imports, Collection<String> staticImports) {
        final List<String> allImports = new ArrayList<String>();
        for (String anImport : imports) {
          if (!anImport.endsWith("*")) {
            allImports.add(anImport); // filter out wildcard imports
          }
        }
        for (final String s : staticImports) {
          int i = s.length() - 1;
          while (s.charAt(i) != '.') {
            i--;
          }
          final String anImport = s.substring(0, i);
          if (!anImport.endsWith("*")) {
            allImports.add(anImport); // filter out wildcard imports
          }
        }

        if (!allImports.isEmpty()) {
          myPostPasses.offer(new Runnable() {
            public void run() {
              final int rootClassName = myContext.get(className.replace(".", "/"));
              final Collection<File> fileNames = myClassToSourceFile.get(rootClassName);
              final ClassRepr repr = fileNames != null && !fileNames.isEmpty()? getReprByName(fileNames.iterator().next(), rootClassName) : null;

              for (final String i : allImports) {
                final int iname = myContext.get(i.replace('.', '/'));
                myClassToClassDependency.put(iname, rootClassName);
                if (repr != null && repr.addUsage(UsageRepr.createClassUsage(myContext, iname))) {
                  for (File fileName : fileNames) {
                    mySourceFileToClasses.put(fileName, repr);
                  }
                }
              }
            }
          });
        }
      }
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    synchronized (myLock) {
      return (Set<ClassRepr>)mySourceFileToClasses.get(new File(sourceFileName));
    }
  }

  @Nullable
  public Collection<File> getClassSources(int className) {
    synchronized (myLock) {
      return myClassToSourceFile.get(className);
    }
  }

  public void close() {
    synchronized (myLock) {
      myClassToSubclasses.close();
      myClassToClassDependency.close();
      mySourceFileToClasses.close();
      myClassToSourceFile.close();

      if (!myIsDelta) {
        myShortClassNameIndex.close();
        // only close if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          context.close();
          myContext = null;
        }
      }
      else {
        if (!myDeltaIsTransient) {
          FileUtil.delete(myRootDir);
        }
      }
    }
  }

  public void flush(final boolean memoryCachesOnly) {
    synchronized (myLock) {
      myClassToSubclasses.flush(memoryCachesOnly);
      myClassToClassDependency.flush(memoryCachesOnly);
      mySourceFileToClasses.flush(memoryCachesOnly);
      myClassToSourceFile.flush(memoryCachesOnly);

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

  private static boolean addAll(final TIntHashSet whereToAdd, TIntHashSet whatToAdd) {
    if (whatToAdd.isEmpty()) {
      return false;
    }
    final Ref<Boolean> changed = new Ref<Boolean>(Boolean.FALSE);
    whatToAdd.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        if (whereToAdd.add(value)) {
          changed.set(Boolean.TRUE);
        }
        return true;
      }
    });
    return changed.get();
  }

  private static void addAllKeys(final TIntHashSet whereToAdd, final IntIntMultiMaplet maplet) {
    maplet.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int key, TIntHashSet b) {
        whereToAdd.add(key);
        return true;
      }
    });
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

  private void addDeletedClass(final ClassRepr cr, File fileName) {
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

    final Collection<File> files = myClassToSourceFile.get(it);

    if (files != null) {
      myChangedFiles.addAll(files);
    }
  }

  @NotNull
  private Set<Pair<ClassRepr, File>> getDeletedClasses() {
    return myDeletedClasses == null ? Collections.<Pair<ClassRepr, File>>emptySet() : Collections.unmodifiableSet(myDeletedClasses);
  }

  @NotNull
  private Set<ClassRepr> getAddedClasses() {
    return myAddedClasses == null ? Collections.<ClassRepr>emptySet() : Collections.unmodifiableSet(myAddedClasses);
  }

  private TIntHashSet getChangedClasses() {
    return myChangedClasses;
  }

  private THashSet<File> getChangedFiles() {
    return myChangedFiles;
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
      mySourceFileToClasses,
      myClassToSourceFile,
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

  public void toStream(File outputRoot) {
    final Streamable[] data = {
      myClassToSubclasses,
      myClassToClassDependency,
      mySourceFileToClasses,
      myClassToSourceFile,
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
      try {
        final PrintStream stream = new PrintStream(file);
        try {
          data[i].toStream(myContext, stream);
        }
        finally {
          stream.close();
        }
      }
      catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }
}