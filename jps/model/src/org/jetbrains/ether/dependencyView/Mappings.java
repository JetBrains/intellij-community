package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IntInlineKeyDescriptor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntProcedure;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.Opcodes;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 16:20
 * To change this template use File | Settings | File Templates.
 */
public class Mappings {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.ether.dependencyView.Mappings");

  private final static String CLASS_TO_SUBCLASSES = "classToSubclasses.tab";
  private final static String CLASS_TO_CLASS = "classToClass.tab";
  private final static String SOURCE_TO_CLASS = "sourceToClass.tab";
  private final static String SOURCE_TO_ANNOTATIONS = "sourceToAnnotations.tab";
  private final static String SOURCE_TO_USAGES = "sourceToUsages.tab";
  private final static String CLASS_TO_SOURCE = "classToSource.tab";
  private static final IntInlineKeyDescriptor INT_KEY_DESCRIPTOR = new IntInlineKeyDescriptor();

  private final boolean myIsDelta;
  private final boolean myDeltaIsTransient;
  private boolean myIsDifferentiated = false;

  private final TIntHashSet myChangedClasses;
  private final TIntHashSet myChangedFiles;
  private final TIntHashSet myDeletedClasses;
  private final Object myLock;

  private void addDeletedClass(final int it) {
    assert (myDeletedClasses != null);

    myDeletedClasses.add(it);

    addChangedClass(it);
  }

  private void addChangedClass(final int it) {
    assert (myChangedClasses != null && myChangedFiles != null);
    myChangedClasses.add(it);

    final Integer file = myClassToSourceFile.get(it);

    if (file != null) {
      myChangedFiles.add(file);
    }

    myIsDifferentiated = true;
  }

  private TIntHashSet getDeletedClasses() {
    return myDeletedClasses;
  }

  private TIntHashSet getChangedClasses() {
    return myChangedClasses;
  }

  private TIntHashSet getChangedFiles() {
    return myChangedFiles;
  }

  private boolean isDifferentiated() {
    return myIsDifferentiated;
  }

  private final File myRootDir;
  private DependencyContext myContext;
  private final int myInitName;
  private org.jetbrains.ether.dependencyView.Logger<Integer> myDebugS;

  private static void debug(final String s) {
    LOG.debug(s);
  }

  private void debug(final String comment, final int s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final String s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final boolean s) {
    myDebugS.debug(comment, s);
  }

  private IntIntMultiMaplet myClassToSubclasses;
  private IntIntMultiMaplet myClassToClassDependency;

  private IntObjectMultiMaplet<ClassRepr> mySourceFileToClasses;
  private IntObjectMultiMaplet<UsageRepr.Usage> mySourceFileToAnnotationUsages;

  private IntObjectMultiMaplet<UsageRepr.Cluster> mySourceFileToUsages;
  private IntIntMaplet myClassToSourceFile;

  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;
  private static final CollectionFactory<ClassRepr> ourClassSetConstructor =
    new CollectionFactory<ClassRepr>() {
      public Set<ClassRepr> create() {
        return new HashSet<ClassRepr>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
      }
    };

  private static final CollectionFactory<UsageRepr.Cluster> ourUsageClusterSetConstructor =
    new CollectionFactory<UsageRepr.Cluster>() {
      public Set<UsageRepr.Cluster> create() {
        return new HashSet<UsageRepr.Cluster>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
      }
    };

  private static final CollectionFactory<UsageRepr.Usage> ourUsageSetConstructor =
    new CollectionFactory<UsageRepr.Usage>() {
      public Set<UsageRepr.Usage> create() {
        return new HashSet<UsageRepr.Usage>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
      }
    };

  private Mappings(final Mappings base) throws IOException {
    myLock = base.myLock;
    myIsDelta = true;
    myPostPasses = new LinkedList<PostPass>();
    myChangedClasses = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myChangedFiles = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myDeletedClasses = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myDeltaIsTransient = base.myDeltaIsTransient;
    myRootDir = new File(FileUtil.toSystemIndependentName(base.myRootDir.getAbsolutePath()) + File.separatorChar + "delta");
    myContext = base.myContext;
    myInitName = myContext.get("<init>");
    myDebugS = base.myDebugS;
    myRootDir.mkdirs();
    createImplementation();
  }

  public Mappings(final File rootDir, final boolean transientDelta) throws IOException {
    myLock = new Object();
    myIsDelta = false;
    myPostPasses = new LinkedList<PostPass>();
    myChangedClasses = null;
    myChangedFiles = null;
    myDeletedClasses = null;
    myDeltaIsTransient = transientDelta;
    myRootDir = rootDir;
    createImplementation();
    myInitName = myContext.get("<init>");
  }

  private void createImplementation() throws IOException {
    if (!myIsDelta) {
      myContext = new DependencyContext(myRootDir);
      myDebugS = myContext.getLogger(LOG);
    }

    if (myIsDelta && myDeltaIsTransient) {
      myClassToSubclasses = new IntIntTransientMultiMaplet();
      myClassToClassDependency = new IntIntTransientMultiMaplet();
      mySourceFileToClasses = new IntObjectTransientMultiMaplet<ClassRepr>(ourClassSetConstructor);
      mySourceFileToAnnotationUsages = new IntObjectTransientMultiMaplet<UsageRepr.Usage>(ourUsageSetConstructor);
      mySourceFileToUsages = new IntObjectTransientMultiMaplet<UsageRepr.Cluster>(ourUsageClusterSetConstructor);
      myClassToSourceFile = new IntIntTransientMaplet();
    }
    else {
      myClassToSubclasses =
        new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_SUBCLASSES), INT_KEY_DESCRIPTOR);
      myClassToClassDependency =
        new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS), INT_KEY_DESCRIPTOR);
      mySourceFileToClasses = new IntObjectPersistentMultiMaplet<ClassRepr>(
        DependencyContext.getTableFile(myRootDir, SOURCE_TO_CLASS), INT_KEY_DESCRIPTOR, ClassRepr.externalizer(myContext),
        ourClassSetConstructor
      );
      mySourceFileToAnnotationUsages = new IntObjectPersistentMultiMaplet<UsageRepr.Usage>(
        DependencyContext.getTableFile(myRootDir, SOURCE_TO_ANNOTATIONS), INT_KEY_DESCRIPTOR, UsageRepr.externalizer(myContext),
        ourUsageSetConstructor
      );
      mySourceFileToUsages = new IntObjectPersistentMultiMaplet<UsageRepr.Cluster>(
        DependencyContext.getTableFile(myRootDir, SOURCE_TO_USAGES), INT_KEY_DESCRIPTOR, UsageRepr.Cluster.clusterExternalizer(myContext),
        ourUsageClusterSetConstructor
      );
      myClassToSourceFile = new IntIntPersistentMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_SOURCE), INT_KEY_DESCRIPTOR);
    }
  }

  public Mappings createDelta() {
    synchronized (myLock) {
      try {
        return new Mappings(this);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void compensateRemovedContent(final Collection<File> compiled) {
    for (File file : compiled) {
      final int fileName = myContext.get(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
      if (!mySourceFileToClasses.containsKey(fileName)) {
        mySourceFileToClasses.put(fileName, new HashSet<ClassRepr>());
      }
    }
  }

  @Nullable
  private ClassRepr getReprByName(final int name) {
    final int source = myClassToSourceFile.get(name);

    if (source > 0) {
      final Collection<ClassRepr> reprs = mySourceFileToClasses.get(source);

      if (reprs != null) {
        for (ClassRepr repr : reprs) {
          if (repr.name == name) {
            return repr;
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

  private static class Option<X> {
    final X myValue;

    Option(final X value) {
      this.myValue = value;
    }

    Option() {
      myValue = null;
    }

    boolean isNone() {
      return myValue == null;
    }

    boolean isValue() {
      return myValue != null;
    }

    X value() {
      return myValue;
    }
  }

  private abstract class PostPass {
    boolean myPerformed = false;

    abstract void perform();

    void run() {
      if (!myPerformed) {
        myPerformed = true;
        perform();
      }
    }
  }

  private final List<PostPass> myPostPasses;


  private void addPostPass(final PostPass p) {
    myPostPasses.add(p);
  }

  private void runPostPasses() {
    myChangedClasses.removeAll(myDeletedClasses.toArray());

    for (final PostPass p : myPostPasses) {
      p.run();
    }
  }

  private static ClassRepr myMockClass = null;
  private static MethodRepr myMockMethod = null;

  private class Util {
    final Mappings myDelta;

    private Util() {
      myDelta = null;
    }

    private Util(Mappings delta) {
      this.myDelta = delta;
    }

    void appendDependents(final ClassRepr c, final TIntHashSet result) {
      final TIntHashSet depClasses = myDelta.myClassToClassDependency.get(c.name);

      if (depClasses != null) {
        addAll(result, depClasses);
      }
    }

    void propagateMemberAccessRec(final TIntHashSet acc, final boolean isField, final boolean root, final int name, final int reflcass) {
      final ClassRepr repr = reprByName(reflcass);

      if (repr != null) {
        if (!root) {
          final Collection members = isField ? repr.fields : repr.methods;

          for (Object o : members) {
            final ProtoMember m = (ProtoMember)o;

            if (m.name == name) {
              return;
            }
          }

          acc.add(reflcass);
        }

        final TIntHashSet subclasses = myClassToSubclasses.get(reflcass);

        if (subclasses != null) {
          subclasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subclass) {
              propagateMemberAccessRec(acc, isField, false, name, subclass);
              return true;
            }
          });
        }
      }
    }

    TIntHashSet propagateMemberAccess(final boolean isField, final int name, final int className) {
      final TIntHashSet acc = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

      propagateMemberAccessRec(acc, isField, true, name, className);

      return acc;
    }

    TIntHashSet propagateFieldAccess(final int name, final int className) {
      return propagateMemberAccess(true, name, className);
    }

    TIntHashSet propagateMethodAccess(final int name, final int className) {
      return propagateMemberAccess(false, name, className);
    }

    MethodRepr.Predicate lessSpecific(final MethodRepr than) {
      return new MethodRepr.Predicate() {
        @Override
        public boolean satisfy(final MethodRepr m) {
          if (m.name == myInitName || m.name != than.name || m.argumentTypes.length != than.argumentTypes.length) {
            return false;
          }

          for (int i = 0; i < than.argumentTypes.length; i++) {
            final Option<Boolean> subtypeOf = isSubtypeOf(than.argumentTypes[i], m.argumentTypes[i]);
            if (subtypeOf.isValue() && !subtypeOf.value()) {
              return false;
            }
          }

          return true;
        }
      };
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridingMethods(final MethodRepr m, final ClassRepr c, final boolean bySpecificity) {
      final Set<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      final MethodRepr.Predicate predicate = bySpecificity ? lessSpecific(m) : MethodRepr.equalByJavaRules(m);

      new Object() {
        public void run(final ClassRepr c) {
          final TIntHashSet subClasses = myClassToSubclasses.get(c.name);

          if (subClasses != null) {
            subClasses.forEach(new TIntProcedure() {
              @Override
              public boolean execute(int subClassName) {
                final ClassRepr r = reprByName(subClassName);

                if (r != null) {
                  boolean cont = true;

                  final Collection<MethodRepr> methods = r.findMethods(predicate);

                  for (MethodRepr mm : methods) {
                    if (isVisibleIn(c, m, r)) {
                      result.add(new Pair<MethodRepr, ClassRepr>(mm, r));
                      cont = false;
                    }
                  }

                  if (cont) {
                    run(r);
                  }
                }
                return true;
              }
            });
          }
        }
      }.run(c);

      return result;
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridenMethods(final MethodRepr m, final ClassRepr c) {
      return findOverridenMethods(m, c, false);
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridenMethods(final MethodRepr m, final ClassRepr c, final boolean bySpecificity) {
      final Set<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      final MethodRepr.Predicate predicate = bySpecificity ? lessSpecific(m) : MethodRepr.equalByJavaRules(m);

      new Object() {
        public void run(final ClassRepr c) {
          final int[] supers = c.getSupers();

          for (int succName : supers) {
            final ClassRepr r = reprByName(succName);

            if (r != null) {
              boolean cont = true;

              final Collection<MethodRepr> methods = r.findMethods(predicate);

              for (MethodRepr mm : methods) {
                if (isVisibleIn(r, mm, c)) {
                  result.add(new Pair<MethodRepr, ClassRepr>(mm, r));
                  cont = false;
                }
              }

              if (cont) {
                run(r);
              }
            }
            else {
              result.add(new Pair<MethodRepr, ClassRepr>(myMockMethod, myMockClass));
            }
          }
        }
      }.run(c);

      return result;
    }

    Collection<Pair<MethodRepr, ClassRepr>> findAllMethodsBySpecificity(final MethodRepr m, final ClassRepr c) {
      final Collection<Pair<MethodRepr, ClassRepr>> result = findOverridenMethods(m, c, true);

      result.addAll(findOverridingMethods(m, c, true));

      return result;
    }

    Collection<Pair<FieldRepr, ClassRepr>> findOverridenFields(final FieldRepr f, final ClassRepr c) {
      final Set<Pair<FieldRepr, ClassRepr>> result = new HashSet<Pair<FieldRepr, ClassRepr>>();

      new Object() {
        public void run(final ClassRepr c) {
          final int[] supers = c.getSupers();

          for (int succName : supers) {
            final ClassRepr r = reprByName(succName);

            if (r != null) {
              boolean cont = true;

              if (r.fields.contains(f)) {
                final FieldRepr ff = r.findField(f.name);

                if (ff != null) {
                  if (isVisibleIn(r, ff, c)) {
                    result.add(new Pair<FieldRepr, ClassRepr>(ff, r));
                    cont = false;
                  }
                }
              }

              if (cont) {
                run(r);
              }
            }
          }
        }
      }.run(c);

      return result;
    }

    ClassRepr reprByName(final int name) {
      if (myDelta != null) {
        final ClassRepr r = myDelta.getReprByName(name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(name);
    }

    Option<Boolean> isInheritorOf(final int who, final int whom) {
      if (who == whom) {
        return new Option<Boolean>(true);
      }

      final ClassRepr repr = reprByName(who);

      if (repr != null) {
        for (int s : repr.getSupers()) {
          final Option<Boolean> inheritorOf = isInheritorOf(s, whom);
          if (inheritorOf.isValue() && inheritorOf.value()) {
            return inheritorOf;
          }
        }
      }

      return new Option<Boolean>();
    }

    Option<Boolean> isSubtypeOf(final TypeRepr.AbstractType who, final TypeRepr.AbstractType whom) {
      if (who.equals(whom)) {
        return new Option<Boolean>(true);
      }

      if (who instanceof TypeRepr.PrimitiveType || whom instanceof TypeRepr.PrimitiveType) {
        return new Option<Boolean>(false);
      }

      if (who instanceof TypeRepr.ArrayType) {
        if (whom instanceof TypeRepr.ArrayType) {
          return isSubtypeOf(((TypeRepr.ArrayType)who).elementType, ((TypeRepr.ArrayType)whom).elementType);
        }

        final String descr = whom.getDescr(myContext);

        if (descr.equals("Ljava/lang/Cloneable") || descr.equals("Ljava/lang/Object") || descr.equals("Ljava/io/Serializable")) {
          return new Option<Boolean>(true);
        }

        return new Option<Boolean>(false);
      }

      if (whom instanceof TypeRepr.ClassType) {
        return isInheritorOf(((TypeRepr.ClassType)who).className, ((TypeRepr.ClassType)whom).className);
      }

      return new Option<Boolean>(false);
    }

    boolean methodVisible(final int className, final MethodRepr m) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.findMethods(MethodRepr.equalByJavaRules(m)).size() > 0) {
          return true;
        }

        return findOverridenMethods(m, r).size() > 0;
      }

      return false;
    }

    boolean fieldVisible(final int className, final FieldRepr field) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.fields.contains(field)) {
          return true;
        }

        return findOverridenFields(field, r).size() > 0;
      }

      return true;
    }

    void affectSubclasses(final int className,
                          final Collection<File> affectedFiles,
                          final Collection<UsageRepr.Usage> affectedUsages,
                          final TIntHashSet dependants,
                          final boolean usages) {
      debug("Affecting subclasses of class: ", className);

      final int fileName = myClassToSourceFile.get(className);

      if (fileName < 0) {
        debug("No source file detected for class ", className);
        debug("End of affectSubclasses");
        return;
      }

      debug("Source file name: ", fileName);

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

      affectedFiles.add(new File(myContext.getValue(fileName)));

      final TIntHashSet directSubclasses = myClassToSubclasses.get(className);

      if (directSubclasses != null) {
        directSubclasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int subClass) {
            affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages);
            return true;
          }
        });
      }
    }

    void affectFieldUsages(final FieldRepr field,
                           final TIntHashSet subclasses,
                           final UsageRepr.Usage rootUsage,
                           final Set<UsageRepr.Usage> affectedUsages,
                           final TIntHashSet dependents) {
      affectedUsages.add(rootUsage);

      subclasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int p) {
          final TIntHashSet deps = myClassToClassDependency.get(p);

          if (deps != null) {
            addAll(dependents, deps);
          }

          debug("Affect field usage referenced of class ", p);
          affectedUsages
            .add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(myContext, p) : field.createUsage(myContext, p));
          return true;
        }
      });
    }

    void affectMethodUsages(final MethodRepr method,
                            final TIntHashSet subclasses,
                            final UsageRepr.Usage rootUsage,
                            final Set<UsageRepr.Usage> affectedUsages,
                            final TIntHashSet dependents) {
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

    void affectAll(final int className, final Collection<File> affectedFiles) {
      final TIntHashSet dependants = myClassToClassDependency.get(className);

      if (dependants != null) {
        final int sourceFile = myClassToSourceFile.get(className);
        if (sourceFile > 0) {
          dependants.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int depClass) {
              final int depFile = myClassToSourceFile.get(depClass);
              if (depFile > 0 && depFile != sourceFile) {
                affectedFiles.add(new File(myContext.getValue(depFile)));
              }
              return true;
            }
          });
        }
      }
    }

    public abstract class UsageConstraint {
      public abstract boolean checkResidence(final int residence);
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
        final Option<Boolean> inheritorOf = isInheritorOf(residence, rootClass);
        return inheritorOf.isNone() || !inheritorOf.value() || super.checkResidence(residence);
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

  private static boolean isPackageLocal(final int access) {
    return (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) == 0;
  }

  private static boolean weakerAccess(final int me, final int then) {
    return ((me & Opcodes.ACC_PRIVATE) > 0 && (then & Opcodes.ACC_PRIVATE) == 0) ||
           ((me & Opcodes.ACC_PROTECTED) > 0 && (then & Opcodes.ACC_PUBLIC) > 0) ||
           (isPackageLocal(me) && (then & Opcodes.ACC_PROTECTED) > 0);
  }

  private static boolean isVisibleIn(final ClassRepr c, final ProtoMember m, final ClassRepr scope) {
    final boolean privacy = ((m.access & Opcodes.ACC_PRIVATE) > 0) && c.name != scope.name;
    final boolean packageLocality = isPackageLocal(m.access) && !c.getPackageName().equals(scope.getPackageName());

    return !privacy && !packageLocality;
  }

  private boolean empty(final int s) {
    return s == myContext.get("");
  }

  private TIntHashSet getAllSubclasses(final int root) {
    final TIntHashSet result = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

    addAllSubclasses(root, result);

    return result;
  }

  private void addAllSubclasses(final int root, final TIntHashSet acc) {
    final TIntHashSet directSubclasses = myClassToSubclasses.get(root);

    acc.add(root);

    if (directSubclasses != null) {
      directSubclasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int s) {
          if (!acc.contains(s)) {
            addAllSubclasses(s, acc);
          }
          return true;
        }
      });
    }
  }

  private boolean incrementalDecision(final int owner,
                                      final Proto member,
                                      final Collection<File> affectedFiles,
                                      final DependentFilesFilter filter) {
    final boolean isField = member instanceof FieldRepr;
    final Util self = new Util(this);

    // Public branch --- hopeless
    if ((member.access & Opcodes.ACC_PUBLIC) > 0) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    // Protected branch
    if ((member.access & Opcodes.ACC_PROTECTED) > 0) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);

      final TIntHashSet propagated = self.propagateFieldAccess(isField ? member.name : myContext.get(""), owner);

      propagated.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int className) {
          final String fileName = myContext.getValue(myClassToSourceFile.get(className));
          debug("Adding ", fileName);
          affectedFiles.add(new File(fileName));
          return true;
        }
      });
    }

    final String packageName = ClassRepr.getPackageName(myContext.getValue(isField ? owner : member.name));

    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);

    // Package-local branch
    myClassToSourceFile.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int className, int fileName) {
        if (ClassRepr.getPackageName(myContext.getValue(className)).equals(packageName)) {
          final String f = myContext.getValue(fileName);
          final File file = new File(f);
          if (filter.accept(file)) {
            debug("Adding: ", f);
            affectedFiles.add(file);
          }
        }
        return true;
      }
    });

    return true;
  }

  public interface DependentFilesFilter {
    DependentFilesFilter ALL_FILES = new DependentFilesFilter() {
      @Override
      public boolean accept(File file) {
        return true;
      }
    };

    boolean accept(File file);
  }

  public boolean differentiate(final Mappings delta,
                               final Collection<String> removed,
                               final Collection<File> filesToCompile,
                               final Collection<File> compiledFiles,
                               final Collection<File> affectedFiles,
                               DependentFilesFilter filter) {
    synchronized (myLock) {
      debug("Begin of Differentiate:");

      delta.runPostPasses();
      delta.compensateRemovedContent(filesToCompile);

      final Util u = new Util(delta);
      final Util self = new Util(this);
      final Util o = new Util();

      if (removed != null) {
        for (String file : removed) {
          final Collection<ClassRepr> classes = mySourceFileToClasses.get(myContext.get(file));

          if (classes != null) {
            for (ClassRepr c : classes) {
              u.affectAll(c.name, affectedFiles);
            }
          }
        }
      }

      class FileClasses {
        final int fileName;
        final Set<ClassRepr> fileClasses;

        FileClasses(int fileName, Collection<ClassRepr> fileClasses) {
          this.fileName = fileName;
          this.fileClasses = new HashSet<ClassRepr>(fileClasses);
        }
      }

      final List<FileClasses> newClasses = new ArrayList<FileClasses>();
      delta.mySourceFileToClasses.forEachEntry(new TIntObjectProcedure<Collection<ClassRepr>>() {
        @Override
        public boolean execute(int fileName, Collection<ClassRepr> classes) {
          newClasses.add(new FileClasses(fileName, classes));
          return true;
        }
      });

      for (final FileClasses compiledFile : newClasses) {
        final int fileName = compiledFile.fileName;
        final Set<ClassRepr> classes = compiledFile.fileClasses;
        final Set<ClassRepr> pastClasses = (Set<ClassRepr>)mySourceFileToClasses.get(fileName);
        final TIntHashSet dependants = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

        final Set<UsageRepr.Usage> affectedUsages = new HashSet<UsageRepr.Usage>();
        final Set<UsageRepr.AnnotationUsage> annotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
        final Map<UsageRepr.Usage, Util.UsageConstraint> usageConstraints = new HashMap<UsageRepr.Usage, Util.UsageConstraint>();

        final Difference.Specifier<ClassRepr> classDiff = Difference.make(pastClasses, classes);

        debug("Processing changed classes:");
        for (final Pair<ClassRepr, Difference> changed : classDiff.changed()) {
          final ClassRepr it = changed.first;
          final ClassRepr.Diff diff = (ClassRepr.Diff)changed.second;

          self.appendDependents(it, dependants);

          delta.addChangedClass(it.name);

          debug("Changed: ", it.name);

          final int addedModifiers = diff.addedModifiers();

          final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) > 0;
          final boolean interfacesChanged = !diff.interfaces().unchanged();
          final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) > 0;

          if (superClassChanged || interfacesChanged || signatureChanged) {
            debug("Superclass changed: ", superClassChanged);
            debug("Interfaces changed: ", interfacesChanged);
            debug("Signature changed ", signatureChanged);

            final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
            final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

            debug("Extends changed: ", extendsChanged);
            debug("Interfaces removed: ", interfacesRemoved);

            u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, extendsChanged || interfacesRemoved || signatureChanged);
          }

          if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) > 0 || (diff.removedModifiers() & Opcodes.ACC_INTERFACE) > 0) {
            debug("Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
            affectedUsages.add(it.createUsage());
          }

          if (it.isAnnotation() && it.policy == RetentionPolicy.SOURCE) {
            debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
            if (!incrementalDecision(it.outerClassName, it, affectedFiles, filter)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }

          if ((addedModifiers & Opcodes.ACC_PROTECTED) > 0) {
            debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
            final UsageRepr.Usage usage = it.createUsage();

            affectedUsages.add(usage);
            usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
          }

          if (diff.packageLocalOn()) {
            debug("Introduction of 'package local' access detected, adding class usage + package constraint to affected usages");
            final UsageRepr.Usage usage = it.createUsage();

            affectedUsages.add(usage);
            usageConstraints.put(usage, u.new PackageConstraint(it.getPackageName()));
          }

          if ((addedModifiers & Opcodes.ACC_FINAL) > 0 || (addedModifiers & Opcodes.ACC_PRIVATE) > 0) {
            debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
            affectedUsages.add(it.createUsage());
          }

          if ((addedModifiers & Opcodes.ACC_ABSTRACT) > 0 || (addedModifiers & Opcodes.ACC_STATIC) > 0) {
            debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
            affectedUsages.add(UsageRepr.createClassNewUsage(myContext, it.name));
          }

          if (it.isAnnotation()) {
            debug("Class is annotation, performing annotation-specific analysis");

            if (diff.retentionChanged()) {
              debug("Retention policy change detected, adding class usage to affected usages");
              affectedUsages.add(it.createUsage());
            }
            else {
              final Collection<ElementType> removedtargets = diff.targets().removed();

              if (removedtargets.contains(ElementType.LOCAL_VARIABLE)) {
                debug("Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
                if (!incrementalDecision(it.outerClassName, it, affectedFiles, filter)) {
                  debug("End of Differentiate, returning false");
                  return false;
                }
              }

              if (!removedtargets.isEmpty()) {
                debug("Removed some annotation targets, adding annotation query");
                annotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr
                  .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), null, removedtargets));
              }

              for (final MethodRepr m : diff.methods().added()) {
                if (!m.hasValue()) {
                  debug("Added method with no default value: ", m.name);
                  debug("Adding class usage to affected usages");
                  affectedUsages.add(it.createUsage());
                }
              }
            }

            debug("End of annotation-specific analysis");
          }

          debug("Processing added methods: ");
          for (final MethodRepr m : diff.methods().added()) {
            debug("Method: ", m.name);

            if (it.isAnnotation()) {
              debug("Class is annotation, skipping method analysis");
              continue;
            }

            if ((it.access & Opcodes.ACC_INTERFACE) > 0 ||
                (it.access & Opcodes.ACC_ABSTRACT) > 0 ||
                (m.access & Opcodes.ACC_ABSTRACT) > 0) {
              debug("Class is abstract, or is interface, or added method in abstract => affecting all subclasses");
              u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
            }

            TIntHashSet propagated = null;

            if ((m.access & Opcodes.ACC_PRIVATE) == 0 && m.name != myInitName) {
              final ClassRepr oldIt = getReprByName(it.name);

              if (oldIt != null && self.findOverridenMethods(m, oldIt).size() > 0) {

              }
              else {
                if (m.argumentTypes.length > 0) {
                  propagated = u.propagateMethodAccess(m.name, it.name);
                  debug("Conservative case on overriding methods, affecting method usages");
                  u.affectMethodUsages(m, propagated, m.createMetaUsage(myContext, it.name), affectedUsages, dependants);
                }
              }
            }

            if ((m.access & Opcodes.ACC_PRIVATE) == 0) {
              final Collection<Pair<MethodRepr, ClassRepr>> affectedMethods = u.findAllMethodsBySpecificity(m, it);
              final MethodRepr.Predicate overrides = MethodRepr.equalByJavaRules(m);

              if (propagated == null) {
                propagated = u.propagateMethodAccess(m.name, it.name);
              }

              final Collection<MethodRepr> lessSpecific = it.findMethods(u.lessSpecific(m));

              for (final MethodRepr mm : lessSpecific) {
                if (!mm.equals(m)) {
                  debug("Found less specific method, affecting method usages");
                  u.affectMethodUsages(mm, propagated, mm.createUsage(myContext, it.name), affectedUsages, dependants);
                }
              }

              debug("Processing affected by specificity methods");
              for (final Pair<MethodRepr, ClassRepr> p : affectedMethods) {
                final MethodRepr mm = p.first;
                final ClassRepr cc = p.second;

                if (cc == myMockClass) {

                }
                else {
                  final Option<Boolean> inheritorOf = self.isInheritorOf(cc.name, it.name);

                  debug("Method: ", mm.name);
                  debug("Class : ", cc.name);

                  if (overrides.satisfy(mm) && inheritorOf.isValue() && inheritorOf.value()) {
                    debug("Current method overrides that found");

                    final Option<Boolean> subtypeOf = u.isSubtypeOf(mm.type, m.type);

                    if (weakerAccess(mm.access, m.access) ||
                        ((m.access & Opcodes.ACC_STATIC) > 0 && (mm.access & Opcodes.ACC_STATIC) == 0) ||
                        ((m.access & Opcodes.ACC_STATIC) == 0 && (mm.access & Opcodes.ACC_STATIC) > 0) ||
                        ((m.access & Opcodes.ACC_FINAL) > 0) ||
                        !m.exceptions.equals(mm.exceptions) ||
                        (subtypeOf.isNone() || !subtypeOf.value()) ||
                        !empty(mm.signature) || !empty(m.signature)) {
                      final int file = myClassToSourceFile.get(cc.name);

                      if (file > 0) {
                        final String f = myContext.getValue(file);
                        debug("Complex condition is satisfied, affecting file ", f);
                        affectedFiles.add(new File(f));
                      }
                    }
                  }
                  else {
                    debug("Current method does not override that found");

                    final TIntHashSet yetPropagated = self.propagateMethodAccess(mm.name, it.name);

                    if (inheritorOf.isValue() && inheritorOf.value()) {
                      final TIntHashSet deps = myClassToClassDependency.get(cc.name);

                      if (deps != null) {
                        addAll(dependants, deps);
                      }

                      u.affectMethodUsages(mm, yetPropagated, mm.createUsage(myContext, cc.name), affectedUsages, dependants);
                    }

                    debug("Affecting method usages for that found");
                    u.affectMethodUsages(mm, yetPropagated, mm.createUsage(myContext, it.name), affectedUsages, dependants);
                  }
                }
              }

              final TIntHashSet subClasses = getAllSubclasses(it.name);

              if (subClasses != null) {
                subClasses.forEach(new TIntProcedure() {
                  @Override
                  public boolean execute(int subClass) {
                    final ClassRepr r = u.reprByName(subClass);
                    final int sourceFileName = myClassToSourceFile.get(subClass);

                    if (r != null && sourceFileName > 0) {
                      final int outerClass = r.outerClassName;

                      if (u.methodVisible(outerClass, m)) {
                        final String f = myContext.getValue(sourceFileName);
                        debug("Affecting file due to local overriding: ", f);
                        affectedFiles.add(new File(f));
                      }
                    }
                    return true;
                  }
                });
              }
            }
          }
          debug("End of added methods processing");

          debug("Processing removed methods:");
          for (final MethodRepr m : diff.methods().removed()) {
            debug("Method ", m.name);

            final Collection<Pair<MethodRepr, ClassRepr>> overridenMethods = u.findOverridenMethods(m, it);
            final TIntHashSet propagated = u.propagateMethodAccess(m.name, it.name);

            if (overridenMethods.size() == 0) {
              debug("No overridden methods found, affecting method usages");
              u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), affectedUsages, dependants);
            }
            else {
              boolean clear = true;

              loop:
              for (final Pair<MethodRepr, ClassRepr> overriden : overridenMethods) {
                final MethodRepr mm = overriden.first;

                if (mm == myMockMethod || !mm.type.equals(m.type) || !empty(mm.signature) || !empty(m.signature)) {
                  clear = false;
                  break loop;
                }
              }

              if (!clear) {
                debug("No clearly overridden methods found, affecting method usages");
                u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), affectedUsages, dependants);
              }
            }

            final Collection<Pair<MethodRepr, ClassRepr>> overriding = u.findOverridingMethods(m, it, false);
            for (final Pair<MethodRepr, ClassRepr> p : overriding) {
              final int fName = myClassToSourceFile.get(p.second.name);
              affectedFiles.add(new File(myContext.getValue(fName)));
            }

            if ((m.access & Opcodes.ACC_ABSTRACT) == 0) {
              propagated.forEach(new TIntProcedure() {
                @Override
                public boolean execute(int p) {
                  if (p != it.name) {
                    final ClassRepr s = u.reprByName(p);

                    if (s != null) {
                      final Collection<Pair<MethodRepr, ClassRepr>> overridenInS = u.findOverridenMethods(m, s);

                      overridenInS.addAll(overridenMethods);

                      boolean allAbstract = true;
                      boolean visited = false;

                      for (final Pair<MethodRepr, ClassRepr> pp : overridenInS) {
                        final ClassRepr cc = pp.second;

                        if (cc == myMockClass) {
                          visited = true;
                          continue;
                        }

                        if (cc.name == it.name) {
                          continue;
                        }

                        visited = true;
                        allAbstract = ((pp.first.access & Opcodes.ACC_ABSTRACT) > 0) || ((cc.access & Opcodes.ACC_INTERFACE) > 0);

                        if (!allAbstract) {
                          break;
                        }
                      }

                      if (allAbstract && visited) {
                        final int source = myClassToSourceFile.get(p);

                        if (source > 0) {
                          final String f = myContext.getValue(source);
                          debug(
                            "Removed method is not abstract & overrides some abstract method which is not then over-overriden in subclass ",
                            p);
                          debug("Affecting subclass source file ", f);
                          affectedFiles.add(new File(f));
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

          debug("Processing changed methods:");
          for (final Pair<MethodRepr, Difference> mr : diff.methods().changed()) {
            final MethodRepr m = mr.first;
            final MethodRepr.Diff d = (MethodRepr.Diff)mr.second;
            final boolean throwsChanged = (d.exceptions().added().size() > 0) || (d.exceptions().changed().size() > 0);

            debug("Method: ", m.name);

            if (it.isAnnotation()) {
              if (d.defaultRemoved()) {
                debug("Class is annotation, default value is removed => adding annotation query");
                final TIntHashSet l = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
                l.add(m.name);
                annotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr
                  .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), l, null));
              }
            }
            else if (d.base() != Difference.NONE || throwsChanged) {
              final TIntHashSet propagated = u.propagateMethodAccess(m.name, it.name);

              boolean affected = false;
              boolean constrained = false;

              final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

              if (d.packageLocalOn()) {
                debug("Method became package-local, affecting method usages outside the package");
                u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);

                for (final UsageRepr.Usage usage : usages) {
                  usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                }

                affectedUsages.addAll(usages);
                affected = true;
                constrained = true;
              }

              if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
                if (!affected) {
                  debug("Return type, throws list or signature changed --- affecting method usages");
                  u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                  affectedUsages.addAll(usages);
                }
              }
              else if ((d.base() & Difference.ACCESS) > 0) {
                if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                    (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                    (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
                  if (!affected) {
                    debug("Added static or private specifier or removed static specifier --- affecting method usages");
                    u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                    affectedUsages.addAll(usages);
                  }

                  if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0) {
                    debug("Added static specifier --- affecting subclasses");
                    u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                  }
                }
                else {
                  if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0 ||
                      (d.addedModifiers() & Opcodes.ACC_PUBLIC) > 0 ||
                      (d.addedModifiers() & Opcodes.ACC_ABSTRACT) > 0) {
                    debug("Added final, public or abstract specifier --- affecting subclasses");
                    u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                  }

                  if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && !((d.removedModifiers() & Opcodes.ACC_PRIVATE) > 0)) {
                    if (!constrained) {
                      debug("Added public or package-local method became protected --- affect method usages with protected constraint");
                      if (!affected) {
                        u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                        affectedUsages.addAll(usages);
                      }

                      for (final UsageRepr.Usage usage : usages) {
                        usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                      }
                    }
                  }
                }
              }
            }
          }
          debug("End of changed methods processing");

          final int mask = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

          debug("Processing added fields");
          for (final FieldRepr f : diff.fields().added()) {
            debug("Field: ", f.name);

            final boolean fPrivate = (f.access & Opcodes.ACC_PRIVATE) > 0;
            final boolean fProtected = (f.access & Opcodes.ACC_PROTECTED) > 0;
            final boolean fPublic = (f.access & Opcodes.ACC_PUBLIC) > 0;
            final boolean fPLocal = !fPrivate && !fProtected && !fPublic;

            if (!fPrivate) {
              final TIntHashSet subClasses = getAllSubclasses(it.name);
              subClasses.forEach(new TIntProcedure() {
                @Override
                public boolean execute(int subClass) {
                  final ClassRepr r = u.reprByName(subClass);
                  final int sourceFileName = myClassToSourceFile.get(subClass);

                  if (r != null && sourceFileName > 0) {
                    if (r.isLocal) {
                      debug(
                        "Affecting local subclass (introduced field can potentially hide surrounding method parameters/local variables): ",
                        sourceFileName);
                      affectedFiles.add(new File(myContext.getValue(sourceFileName)));
                    }
                    else {
                      final int outerClass = r.outerClassName;

                      if (!empty(outerClass) && u.fieldVisible(outerClass, f)) {
                        debug("Affecting inner subclass (introduced field can potentially hide surrounding class fields): ",
                              sourceFileName);
                        affectedFiles.add(new File(myContext.getValue(sourceFileName)));
                      }
                    }
                  }

                  debug("Affecting field usages referenced from subclass ", subClass);
                  final TIntHashSet propagated = u.propagateFieldAccess(f.name, subClass);
                  u.affectFieldUsages(f, propagated, f.createUsage(myContext, subClass), affectedUsages, dependants);

                  final TIntHashSet deps = myClassToClassDependency.get(subClass);

                  if (deps != null) {
                    addAll(dependants, deps);
                  }
                  return true;
                }
              });
            }

            final Collection<Pair<FieldRepr, ClassRepr>> overridden = u.findOverridenFields(f, it);

            for (final Pair<FieldRepr, ClassRepr> p : overridden) {
              final FieldRepr ff = p.first;
              final ClassRepr cc = p.second;

              final boolean ffPrivate = (ff.access & Opcodes.ACC_PRIVATE) > 0;
              final boolean ffProtected = (ff.access & Opcodes.ACC_PROTECTED) > 0;
              final boolean ffPublic = (ff.access & Opcodes.ACC_PUBLIC) > 0;
              final boolean ffPLocal = isPackageLocal(ff.access);

              if (!ffPrivate) {
                final TIntHashSet propagated = o.propagateFieldAccess(ff.name, cc.name);
                final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

                debug("Affecting usages of overridden field in class ", cc.name);
                u.affectFieldUsages(ff, propagated, ff.createUsage(myContext, cc.name), localUsages, dependants);

                if (fPrivate || (fPublic && (ffPublic || ffPLocal)) || (fProtected && ffProtected) || (fPLocal && ffPLocal)) {

                }
                else {
                  Util.UsageConstraint constaint;

                  if ((ffProtected && fPublic) || (fProtected && ffPublic) || (ffPLocal && fProtected)) {
                    constaint = u.new NegationConstraint(u.new InheritanceConstraint(cc.name));
                  }
                  else if (ffPublic && ffPLocal) {
                    constaint = u.new NegationConstraint(u.new PackageConstraint(cc.getPackageName()));
                  }
                  else {
                    constaint = u.new IntersectionConstraint(u.new NegationConstraint(u.new InheritanceConstraint(cc.name)),
                                                             u.new NegationConstraint(u.new PackageConstraint(cc.getPackageName())));
                  }

                  for (final UsageRepr.Usage usage : localUsages) {
                    usageConstraints.put(usage, constaint);
                  }
                }

                affectedUsages.addAll(localUsages);
              }
            }
          }
          debug("End of added fields processing");

          debug("Processing removed fields:");
          for (final FieldRepr f : diff.fields().removed()) {
            debug("Field: ", f.name);

            if ((f.access & Opcodes.ACC_PRIVATE) == 0 && (f.access & mask) == mask && f.hasValue()) {
              debug("Field had value and was (non-private) final static => a switch to non-incremental mode requested");
              if (!incrementalDecision(it.name, f, affectedFiles, filter)) {
                debug("End of Differentiate, returning false");
                return false;
              }
            }

            final TIntHashSet propagated = u.propagateFieldAccess(f.name, it.name);
            u.affectFieldUsages(f, propagated, f.createUsage(myContext, it.name), affectedUsages, dependants);
          }
          debug("End of removed fields processing");

          debug("Processing changed fields:");
          for (final Pair<FieldRepr, Difference> f : diff.fields().changed()) {
            final Difference d = f.second;
            final FieldRepr field = f.first;

            debug("Field: ", it.name);

            if ((field.access & Opcodes.ACC_PRIVATE) == 0 && (field.access & mask) == mask) {
              if ((d.base() & Difference.ACCESS) > 0 || ((d.base() & Difference.VALUE) > 0 && d.hadValue())) {
                debug("Inline field changed it's access or value => a switch to non-incremental mode requested");
                if (!incrementalDecision(it.name, field, affectedFiles, filter)) {
                  debug("End of Differentiate, returning false");
                  return false;
                }
              }
            }

            if (d.base() != Difference.NONE) {
              final TIntHashSet propagated = u.propagateFieldAccess(field.name, it.name);

              if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
                debug("Type or signature changed --- affecting field usages");
                u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), affectedUsages, dependants);
              }
              else if ((d.base() & Difference.ACCESS) > 0) {
                if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                    (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                    (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                    (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
                  debug("Added/removed static modifier or added private/volatile modifier --- affecting field usages");
                  u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), affectedUsages, dependants);
                }
                else {
                  boolean affected = false;
                  final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

                  if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                    debug("Added final modifier --- affecting field assign usages");
                    u.affectFieldUsages(field, propagated, field.createAssignUsage(myContext, it.name), usages, dependants);
                    affectedUsages.addAll(usages);
                    affected = true;
                  }

                  if ((d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                    debug("Removed public modifier, affecting field usages with appropriate constraint");
                    if (!affected) {
                      u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), usages, dependants);
                      affectedUsages.addAll(usages);
                    }

                    for (final UsageRepr.Usage usage : usages) {
                      if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0) {
                        usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                      }
                      else {
                        usageConstraints.put(usage, u.new PackageConstraint(it.getPackageName()));
                      }
                    }
                  }
                }
              }
            }
          }
          debug("End of changed fields processing");
        }
        debug("End of changed classes processing");

        debug("Processing removed classes:");
        for (final ClassRepr c : classDiff.removed()) {
          delta.addDeletedClass(c.name);
          self.appendDependents(c, dependants);
          debug("Adding usages of class ", c.name);
          affectedUsages.add(c.createUsage());
        }
        debug("End of removed classes processing.");

        debug("Processing added classes:");
        for (final ClassRepr c : classDiff.added()) {
          delta.addChangedClass(c.name);

          final TIntHashSet depClasses = myClassToClassDependency.get(c.name);

          if (depClasses != null) {
            depClasses.forEach(new TIntProcedure() {
              @Override
              public boolean execute(int depClass) {
                final int fName = myClassToSourceFile.get(depClass);

                if (fName > 0) {
                  final String f = myContext.getValue(fName);
                  debug("Adding dependent file ", f);
                  affectedFiles.add(new File(f));
                }
                return true;
              }
            });
          }
        }
        debug("End of added classes processing.");

        debug("Checking dependent files:");
        final TIntHashSet dependentFiles = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

        dependants.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int depClass) {
            final int file = myClassToSourceFile.get(depClass);
            if (file > 0) {
              dependentFiles.add(file);
            }
            return true;
          }
        });


        filewise:
        for (final int depFile : dependentFiles.toArray()) {  // todo: avoid toArray()?
          final File theFile = new File(myContext.getValue(depFile));

          if (affectedFiles.contains(theFile) || compiledFiles.contains(theFile)) {
            continue filewise;
          }

          debug("Dependent file: ", depFile);
          final Collection<UsageRepr.Cluster> depClusters = mySourceFileToUsages.get(depFile);
          if (depClusters != null) {
            for (final UsageRepr.Cluster depCluster : depClusters) {
              final Set<UsageRepr.Usage> depUsages = depCluster.getUsages();
              if (depUsages == null) {
                continue;
              }
              final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>(depUsages);

              usages.retainAll(affectedUsages);

              if (!usages.isEmpty()) {
                for (UsageRepr.Usage usage : usages) {
                  final Util.UsageConstraint constraint = usageConstraints.get(usage);

                  if (constraint == null) {
                    debug("Added file with no constraints");
                    affectedFiles.add(theFile);
                    continue filewise;
                  }
                  else {
                    final TIntHashSet residenceClasses = depCluster.getResidence(usage);
                    for (final int residentName : residenceClasses.toArray()) {
                      if (constraint.checkResidence(residentName)) {
                        debug("Added file with satisfied constraint");
                        affectedFiles.add(theFile);
                        continue filewise;
                      }
                    }
                  }
                }
              }

              if (annotationQuery.size() > 0) {
                final Collection<UsageRepr.Usage> annotationUsages = mySourceFileToAnnotationUsages.get(depFile);

                for (final UsageRepr.Usage usage : annotationUsages) {
                  for (final UsageRepr.AnnotationUsage query : annotationQuery) {
                    if (query.satisfies(usage)) {
                      debug("Added file due to annotation query");
                      affectedFiles.add(theFile);
                      continue filewise;
                    }
                  }
                }
              }
            }
          }
        }
      }

      if (removed != null) {
        for (final String r : removed) {
          affectedFiles.remove(new File(r));
        }
      }

      debug("End of Differentiate, returning true");
      return true;
    }
  }

  private void cleanupRemovedClass(final int className, ClassRepr cr) {
    if (cr == null) {
    cr = new Util().reprByName(className);
    }

    if (cr != null) {
      for (final int superSomething : cr.getSupers()) {
        myClassToSubclasses.removeFrom(superSomething, cr.name);
      }

      final int sourceFile = myClassToSourceFile.get(className);

      if (sourceFile > 0) {
        final Collection<UsageRepr.Cluster> clusters = mySourceFileToUsages.get(sourceFile);

        if (clusters != null) {
          for (final UsageRepr.Cluster cluster : clusters) {
            final Set<UsageRepr.Usage> usages = cluster.getUsages();
            if (usages != null) {
              for (final UsageRepr.Usage u : usages) {
                if (u instanceof UsageRepr.ClassUsage) {
                  final TIntHashSet residents = cluster.getResidence(u);

                  if (residents != null && residents.contains(cr.name)) {
                    myClassToClassDependency.removeFrom(((UsageRepr.ClassUsage)u).className, cr.name);
                  }
                }
              }
            }
          }
        }
      }
    }

    myClassToClassDependency.remove(className);
    myClassToSubclasses.remove(className);
    myClassToSourceFile.remove(className);
  }

  public void integrate(final Mappings delta, final Collection<File> compiled, final Collection<String> removed) {
    synchronized (myLock) {
      try {
        delta.runPostPasses();

        if (removed != null) {
          for (final String file : removed) {
            final int key = myContext.get(file);
            final Set<ClassRepr> classes = (Set<ClassRepr>)mySourceFileToClasses.get(key);
            final Collection<UsageRepr.Cluster> clusters = mySourceFileToUsages.get(key);

            if (classes != null) {
              for (final ClassRepr cr : classes) {
                cleanupRemovedClass(cr.name, cr);
              }
            }

            mySourceFileToClasses.remove(key);
            mySourceFileToUsages.remove(key);
            mySourceFileToAnnotationUsages.remove(key);
          }
        }

        if (delta.isDifferentiated()) {
          delta.getDeletedClasses().forEach(new TIntProcedure() {
            @Override
            public boolean execute(int value) {
              cleanupRemovedClass(value, null);
              return true;
            }
          });

          delta.getChangedClasses().forEach(new TIntProcedure() {
            @Override
            public boolean execute(int c) {
              final TIntHashSet subClasses = delta.myClassToSubclasses.get(c);
              if (subClasses != null) {
                myClassToSubclasses.replace(c, subClasses);
              }
              else {
                myClassToSubclasses.remove(c);
              }

              final int sourceFile = delta.myClassToSourceFile.get(c);
              if (sourceFile > 0) {
                myClassToSourceFile.put(c, sourceFile);
              }
              else {
                myClassToSourceFile.remove(c);
              }
              return true;
            }
          });

          delta.getChangedFiles().forEach(new TIntProcedure() {
            @Override
            public boolean execute(int f) {
              final Collection<ClassRepr> classes = delta.mySourceFileToClasses.get(f);
              if (classes != null) {
                mySourceFileToClasses.replace(f, classes);
              }
              else {
                mySourceFileToClasses.remove(f);
              }

              final Collection<UsageRepr.Cluster> clusters = delta.mySourceFileToUsages.get(f);
              if (clusters != null) {
                mySourceFileToUsages.replace(f, clusters);
              }
              else {
                mySourceFileToUsages.remove(f);
              }

              final Collection<UsageRepr.Usage> usages = delta.mySourceFileToAnnotationUsages.get(f);
              if (usages != null) {
                mySourceFileToAnnotationUsages.replace(f, usages);
              }
              else {
                mySourceFileToAnnotationUsages.remove(f);
              }
              return true;
            }
          });
        }
        else {
          myClassToSubclasses.putAll(delta.myClassToSubclasses);
          myClassToSourceFile.putAll(delta.myClassToSourceFile);

          mySourceFileToClasses.replaceAll(delta.mySourceFileToClasses);
          mySourceFileToUsages.replaceAll(delta.mySourceFileToUsages);
          mySourceFileToAnnotationUsages.replaceAll(delta.mySourceFileToAnnotationUsages);
        }

        final int[] compiledClasses = getClassNames(compiled);
        final int[] changedClassesArray = delta.getChangedClasses().toArray();

        delta.myClassToClassDependency.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
          @Override
          public boolean execute(int aClass, TIntHashSet now) {
            if (delta.isDifferentiated()) {
              final boolean classChanged = delta.getChangedClasses().contains(aClass);
              final TIntHashSet depClasses = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
              addAll(depClasses, now);

              depClasses.retainAll(changedClassesArray);

              if (!classChanged && depClasses.isEmpty()) {
                return true;
              }
            }

            final TIntHashSet past = myClassToClassDependency.get(aClass);

            if (past == null) {
              myClassToClassDependency.put(aClass, now);
            }
            else {
              boolean changed = past.removeAll(compiledClasses);
              changed |= addAll(past, now);

              if (changed) {
                myClassToClassDependency.replace(aClass, past);
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

  private int[] getClassNames(Collection<File> compiled) {
    final TIntHashSet classnames = new TIntHashSet(compiled.size());
    for (final File c : compiled) {
      final int fileName = myContext.get(FileUtil.toSystemIndependentName(c.getAbsolutePath()));
      final Collection<ClassRepr> reprs = mySourceFileToClasses.get(fileName);
      if (reprs != null) {
        for (final ClassRepr repr : reprs) {
          classnames.add(repr.name);
        }
      }
    }
    return classnames.toArray();
  }

  public Callbacks.Backend getCallback() {
    return new Callbacks.Backend() {
      public Collection<String> getClassFiles() {
        final Set<String> result = new HashSet<String>();

        synchronized (myLock) {
          myClassToSourceFile.forEachEntry(new TIntIntProcedure() {
            @Override
            public boolean execute(int s, int b) {
              result.add(myContext.getValue(s));
              return true;
            }
          });
        }

        return result;
      }

      public void associate(final String classFileName, final Callbacks.SourceFileNameLookup sourceFileName, final ClassReader cr) {
        synchronized (myLock) {
          final int classFileNameS = myContext.get(classFileName);
          final Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> result =
            new ClassfileAnalyzer(myContext).analyze(classFileNameS, cr);
          final ClassRepr repr = result.first;
          final UsageRepr.Cluster localUsages = result.second.first;
          final Set<UsageRepr.Usage> localAnnotationUsages = result.second.second;

          final String srcFileName = sourceFileName.get(repr == null ? null : myContext.getValue(repr.getSourceFileName()));
          final int sourceFileNameS = myContext.get(srcFileName);

          if (repr != null) {
            final int className = repr.name;

            myClassToSourceFile.put(repr.name, sourceFileNameS);
            mySourceFileToClasses.put(sourceFileNameS, repr);

            for (final int s : repr.getSupers()) {
              myClassToSubclasses.put(s, repr.name);
            }

            for (final UsageRepr.Usage u : localUsages.getUsages()) {
              final int owner = u.getOwner();

              if (owner != className) {
                final int sourceFile = repr.getSourceFileName();
                final int ownerSourceFile = myClassToSourceFile.get(owner);

                if (ownerSourceFile > 0) {
                  if (ownerSourceFile != sourceFile) {
                    myClassToClassDependency.put(owner, className);
                  }
                }
                else {
                  myClassToClassDependency.put(owner, className);
                }
              }
            }
          }

          if (!localUsages.isEmpty()) {
            mySourceFileToUsages.put(sourceFileNameS, localUsages);
          }

          if (!localAnnotationUsages.isEmpty()) {
            mySourceFileToAnnotationUsages.put(sourceFileNameS, localAnnotationUsages);
          }
        }
      }

      @Override
      public void registerConstantUsage(final String className, final String fieldName, final String fieldOwner) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void registerImports(final String className, final Collection<String> imports, Collection<String> staticImports) {
        for (final String s : staticImports) {
          int i = s.length() - 1;
          for (; s.charAt(i) != '.'; i--) ;
          imports.add(s.substring(0, i));
        }

        addPostPass(new PostPass() {
          public void perform() {
            final int rootClassName = myContext.get(className.replace(".", "/"));
            final int fileName = myClassToSourceFile.get(rootClassName);

            for (final String i : imports) {
              if (i.endsWith("*")) {
                continue; // filter out wildcard imports
              }
              final int iname = myContext.get(i.replace(".", "/"));

              myClassToClassDependency.put(iname, rootClassName);

              if (fileName > 0) {
                final UsageRepr.Cluster cluster = new UsageRepr.Cluster();
                cluster.addUsage(rootClassName, UsageRepr.createClassUsage(myContext, iname));
                mySourceFileToUsages.put(fileName, cluster);
              }
            }
          }
        });
      }
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    synchronized (myLock) {
      return (Set<ClassRepr>)mySourceFileToClasses.get(myContext.get(sourceFileName));
    }
  }

  public void close() {
    synchronized (myLock) {
      myClassToSubclasses.close();
      myClassToClassDependency.close();
      mySourceFileToClasses.close();
      mySourceFileToAnnotationUsages.close();
      mySourceFileToUsages.close();
      myClassToSourceFile.close();

      if (!myIsDelta) {
        // only close if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          context.close();
          myContext = null;
        }
      }
      else {
        FileUtil.delete(myRootDir);
      }
    }
  }

  public void flush(final boolean memoryCachesOnly) {
    synchronized (myLock) {
      myClassToSubclasses.flush(memoryCachesOnly);
      myClassToClassDependency.flush(memoryCachesOnly);
      mySourceFileToClasses.flush(memoryCachesOnly);
      mySourceFileToAnnotationUsages.flush(memoryCachesOnly);
      mySourceFileToUsages.flush(memoryCachesOnly);
      myClassToSourceFile.flush(memoryCachesOnly);

      if (!myIsDelta) {
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
}
