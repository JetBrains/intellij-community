package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.ether.dependencyView.Mappings");

  private final static String CLASS_TO_SUBCLASSES = "classToSubclasses.tab";
  private final static String CLASS_TO_CLASS = "classToClass.tab";
  private final static String SOURCE_TO_CLASS = "sourceToClass.tab";
  private final static String SOURCE_TO_ANNOTATIONS = "sourceToAnnotations.tab";
  private final static String SOURCE_TO_USAGES = "sourceToUsages.tab";
  private final static String CLASS_TO_SOURCE = "classToSource.tab";

  private final boolean myIsDelta;
  private final boolean myDeltaIsTransient;

  private final File myRootDir;
  private DependencyContext myContext;
  private org.jetbrains.ether.dependencyView.Logger<DependencyContext.S> myDebugS;

  private void debug(final String s) {
    LOG.debug(s);
  }

  private void debug(final DependencyContext.S s) {
    myDebugS.debug(s);
  }

  private MultiMaplet<DependencyContext.S, DependencyContext.S> myClassToSubclasses;
  private MultiMaplet<DependencyContext.S, DependencyContext.S> myClassToClassDependency;

  private MultiMaplet<DependencyContext.S, ClassRepr> mySourceFileToClasses;
  private MultiMaplet<DependencyContext.S, UsageRepr.Usage> mySourceFileToAnnotationUsages;

  private MultiMaplet<DependencyContext.S, UsageRepr.Cluster> mySourceFileToUsages;
  private Maplet<DependencyContext.S, DependencyContext.S> myClassToSourceFile;

  private static final TransientMultiMaplet.CollectionConstructor<ClassRepr> ourClassSetConstructor =
    new TransientMultiMaplet.CollectionConstructor<ClassRepr>() {
      public Set<ClassRepr> create() {
        return new HashSet<ClassRepr>();
      }
    };

  private static final TransientMultiMaplet.CollectionConstructor<UsageRepr.Cluster> ourUsageClusterSetConstructor =
    new TransientMultiMaplet.CollectionConstructor<UsageRepr.Cluster>() {
      public Set<UsageRepr.Cluster> create() {
        return new HashSet<UsageRepr.Cluster>();
      }
    };

  private static final TransientMultiMaplet.CollectionConstructor<UsageRepr.Usage> ourUsageSetConstructor =
    new TransientMultiMaplet.CollectionConstructor<UsageRepr.Usage>() {
      public Set<UsageRepr.Usage> create() {
        return new HashSet<UsageRepr.Usage>();
      }
    };

  private static final TransientMultiMaplet.CollectionConstructor<DependencyContext.S> ourStringSetConstructor =
    new TransientMultiMaplet.CollectionConstructor<DependencyContext.S>() {
      public Set<DependencyContext.S> create() {
        return new HashSet<DependencyContext.S>();
      }
    };

  private Mappings(final Mappings base) throws IOException {
    myIsDelta = true;
    myDeltaIsTransient = base.myDeltaIsTransient;
    myRootDir = new File(FileUtil.toSystemIndependentName(base.myRootDir.getAbsolutePath()) + File.separatorChar + "delta");
    myContext = base.myContext;
    myDebugS = base.myDebugS;
    myRootDir.mkdirs();
    createImplementation();
  }

  public Mappings(final File rootDir, final boolean transientDelta) throws IOException {
    myIsDelta = false;
    myDeltaIsTransient = transientDelta;
    myRootDir = rootDir;
    createImplementation();
  }

  private void createImplementation() throws IOException {
    if (!myIsDelta) {
      myContext = new DependencyContext(myRootDir);
      myDebugS = myContext.getLogger(LOG);
    }

    if (myIsDelta && myDeltaIsTransient) {
      myClassToSubclasses = new TransientMultiMaplet<DependencyContext.S, DependencyContext.S>(ourStringSetConstructor);
      myClassToClassDependency = new TransientMultiMaplet<DependencyContext.S, DependencyContext.S>(ourStringSetConstructor);
      mySourceFileToClasses = new TransientMultiMaplet<DependencyContext.S, ClassRepr>(ourClassSetConstructor);
      mySourceFileToAnnotationUsages = new TransientMultiMaplet<DependencyContext.S, UsageRepr.Usage>(ourUsageSetConstructor);
      mySourceFileToUsages = new TransientMultiMaplet<DependencyContext.S, UsageRepr.Cluster>(ourUsageClusterSetConstructor);
      myClassToSourceFile = new TransientMaplet<DependencyContext.S, DependencyContext.S>();
    }
    else {
      myClassToSubclasses =
        new PersistentMultiMaplet<DependencyContext.S, DependencyContext.S>(DependencyContext.getTableFile(myRootDir, CLASS_TO_SUBCLASSES),
                                                                            DependencyContext.descriptorS, DependencyContext.descriptorS,
                                                                            ourStringSetConstructor);

      myClassToClassDependency =
        new PersistentMultiMaplet<DependencyContext.S, DependencyContext.S>(DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS),
                                                                            DependencyContext.descriptorS, DependencyContext.descriptorS,
                                                                            ourStringSetConstructor);

      mySourceFileToClasses =
        new PersistentMultiMaplet<DependencyContext.S, ClassRepr>(DependencyContext.getTableFile(myRootDir, SOURCE_TO_CLASS),
                                                                  DependencyContext.descriptorS, ClassRepr.externalizer(myContext),
                                                                  ourClassSetConstructor);

      mySourceFileToAnnotationUsages =
        new PersistentMultiMaplet<DependencyContext.S, UsageRepr.Usage>(DependencyContext.getTableFile(myRootDir, SOURCE_TO_ANNOTATIONS),
                                                                        DependencyContext.descriptorS, UsageRepr.externalizer(myContext),
                                                                        ourUsageSetConstructor);

      mySourceFileToUsages =
        new PersistentMultiMaplet<DependencyContext.S, UsageRepr.Cluster>(DependencyContext.getTableFile(myRootDir, SOURCE_TO_USAGES),
                                                                          DependencyContext.descriptorS,
                                                                          UsageRepr.Cluster.clusterExternalizer(myContext),
                                                                          ourUsageClusterSetConstructor);

      myClassToSourceFile =
        new PersistentMaplet<DependencyContext.S, DependencyContext.S>(DependencyContext.getTableFile(myRootDir, CLASS_TO_SOURCE),
                                                                       DependencyContext.descriptorS, DependencyContext.descriptorS);
    }
  }

  public Mappings createDelta() {
    try {
      return new Mappings(this);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void clearMemoryCaches() {
    myContext.clearMemoryCaches();
  }

  private void compensateRemovedContent(final Collection<File> compiled) {
    for (File file : compiled) {
      final DependencyContext.S key = myContext.get(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
      if (!mySourceFileToClasses.containsKey(key)) {
        mySourceFileToClasses.put(key, new HashSet<ClassRepr>());
      }
    }
  }

  @Nullable
  private ClassRepr getReprByName(final DependencyContext.S name) {
    final DependencyContext.S source = myClassToSourceFile.get(name);

    if (source != null) {
      final Collection<ClassRepr> reprs = mySourceFileToClasses.get(source);

      if (reprs != null) {
        for (ClassRepr repr : reprs) {
          if (repr.name.equals(name)) {
            return repr;
          }
        }
      }
    }

    return null;
  }

  public void clean() throws IOException {
    if (myRootDir != null) {
      close();
      FileUtil.delete(myRootDir);
      createImplementation();
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

    void appendDependents(final Set<ClassRepr> classes, final Set<DependencyContext.S> result) {
      if (classes == null) {
        return;
      }

      for (ClassRepr c : classes) {
        final Collection<DependencyContext.S> depClasses = myDelta.myClassToClassDependency.get(c.name);

        if (depClasses != null) {
          for (DependencyContext.S className : depClasses) {
            result.add(className);
          }
        }
      }
    }

    void propagateMemberAccessRec(final Collection<DependencyContext.S> acc,
                                  final boolean isField,
                                  final boolean root,
                                  final DependencyContext.S name,
                                  final DependencyContext.S reflcass) {
      final ClassRepr repr = reprByName(reflcass);

      if (repr != null) {
        if (!root) {
          final Collection members = isField ? repr.fields : repr.methods;

          for (Object o : members) {
            final ProtoMember m = (ProtoMember)o;

            if (m.name.equals(name)) {
              return;
            }
          }

          acc.add(reflcass);
        }

        final Collection<DependencyContext.S> subclasses = myClassToSubclasses.get(reflcass);

        if (subclasses != null) {
          for (DependencyContext.S subclass : subclasses) {
            propagateMemberAccessRec(acc, isField, false, name, subclass);
          }
        }
      }
    }

    Collection<DependencyContext.S> propagateMemberAccess(final boolean isField,
                                                          final DependencyContext.S name,
                                                          final DependencyContext.S className) {
      final Set<DependencyContext.S> acc = new HashSet<DependencyContext.S>();

      propagateMemberAccessRec(acc, isField, true, name, className);

      return acc;
    }

    Collection<DependencyContext.S> propagateFieldAccess(final DependencyContext.S name, final DependencyContext.S className) {
      return propagateMemberAccess(true, name, className);
    }

    Collection<DependencyContext.S> propagateMethodAccess(final DependencyContext.S name, final DependencyContext.S className) {
      return propagateMemberAccess(false, name, className);
    }

    MethodRepr.Predicate lessSpecific(final MethodRepr than) {
      return new MethodRepr.Predicate() {
        @Override
        public boolean satisfy(final MethodRepr m) {
          if (!m.name.equals(than.name) || m.argumentTypes.length != than.argumentTypes.length) {
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
          final Collection<DependencyContext.S> subClasses = myClassToSubclasses.get(c.name);

          if (subClasses != null) {
            for (DependencyContext.S subClassName : subClasses) {
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
            }
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
          final DependencyContext.S[] supers = c.getSupers();

          for (DependencyContext.S succName : supers) {
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
          final DependencyContext.S[] supers = c.getSupers();

          for (DependencyContext.S succName : supers) {
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

    ClassRepr reprByName(final DependencyContext.S name) {
      if (myDelta != null) {
        final ClassRepr r = myDelta.getReprByName(name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(name);
    }

    Option<Boolean> isInheritorOf(final DependencyContext.S who, final DependencyContext.S whom) {
      if (who.equals(whom)) {
        return new Option<Boolean>(true);
      }

      final ClassRepr repr = reprByName(who);

      if (repr != null) {
        for (DependencyContext.S s : repr.getSupers()) {
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

    boolean methodVisible(final DependencyContext.S className, final MethodRepr m) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.findMethods(MethodRepr.equalByJavaRules(m)).size() > 0) {
          return true;
        }

        return findOverridenMethods(m, r).size() > 0;
      }

      return false;
    }

    boolean fieldVisible(final DependencyContext.S className, final FieldRepr field) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.fields.contains(field)) {
          return true;
        }

        return findOverridenFields(field, r).size() > 0;
      }

      return true;
    }

    void affectSubclasses(final DependencyContext.S className,
                          final Collection<File> affectedFiles,
                          final Collection<UsageRepr.Usage> affectedUsages,
                          final Collection<DependencyContext.S> dependants,
                          final boolean usages) {
      final DependencyContext.S fileName = myClassToSourceFile.get(className);

      if (fileName == null) {
        return;
      }

      if (usages) {
        final ClassRepr classRepr = reprByName(className);

        if (classRepr != null) {
          affectedUsages.add(classRepr.createUsage());
        }
      }

      final Collection<DependencyContext.S> depClasses = myClassToClassDependency.get(fileName);

      if (depClasses != null) {
        dependants.addAll(depClasses);
      }

      affectedFiles.add(new File(myContext.getValue(fileName)));

      final Collection<DependencyContext.S> directSubclasses = myClassToSubclasses.get(className);

      if (directSubclasses != null) {
        for (DependencyContext.S subClass : directSubclasses) {
          affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages);
        }
      }
    }

    void affectFieldUsages(final FieldRepr field,
                           final Collection<DependencyContext.S> subclasses,
                           final UsageRepr.Usage rootUsage,
                           final Set<UsageRepr.Usage> affectedUsages,
                           final Set<DependencyContext.S> dependents) {
      affectedUsages.add(rootUsage);

      for (DependencyContext.S p : subclasses) {
        final Collection<DependencyContext.S> deps = myClassToClassDependency.get(p);

        if (deps != null) {
          dependents.addAll(deps);
        }

        affectedUsages
          .add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(myContext, p) : field.createUsage(myContext, p));
      }
    }

    void affectMethodUsages(final MethodRepr method,
                            final Collection<DependencyContext.S> subclasses,
                            final UsageRepr.Usage rootUsage,
                            final Set<UsageRepr.Usage> affectedUsages,
                            final Set<DependencyContext.S> dependents) {
      affectedUsages.add(rootUsage);

      for (DependencyContext.S p : subclasses) {
        final Collection<DependencyContext.S> deps = myClassToClassDependency.get(p);

        if (deps != null) {
          dependents.addAll(deps);
        }

        affectedUsages
          .add(rootUsage instanceof UsageRepr.MetaMethodUsage ? method.createMetaUsage(myContext, p) : method.createUsage(myContext, p));
      }
    }

    void affectAll(final DependencyContext.S className, final Collection<File> affectedFiles) {
      final Set<DependencyContext.S> dependants = (Set<DependencyContext.S>)myClassToClassDependency.get(className);
      final DependencyContext.S sourceFile = myClassToSourceFile.get(className);

      if (dependants != null) {
        for (DependencyContext.S depClass : dependants) {
          final DependencyContext.S depFile = myClassToSourceFile.get(depClass);

          if (depFile != null && sourceFile != null && !depFile.equals(sourceFile)) {
            affectedFiles.add(new File(myContext.getValue(depFile)));
          }
        }
      }
    }

    public abstract class UsageConstraint {
      public abstract boolean checkResidence(final DependencyContext.S residence);
    }

    public class PackageConstraint extends UsageConstraint {
      public final String packageName;

      public PackageConstraint(final String packageName) {
        this.packageName = packageName;
      }

      @Override
      public boolean checkResidence(final DependencyContext.S residence) {
        return !ClassRepr.getPackageName(myContext.getValue(residence)).equals(packageName);
      }
    }

    public class InheritanceConstraint extends UsageConstraint {
      public final DependencyContext.S rootClass;

      public InheritanceConstraint(final DependencyContext.S rootClass) {
        this.rootClass = rootClass;
      }

      @Override
      public boolean checkResidence(final DependencyContext.S residence) {
        final Option<Boolean> inheritorOf = isInheritorOf(residence, rootClass);
        return inheritorOf.isNone() || !inheritorOf.value();
      }
    }

    public class NegationConstraint extends UsageConstraint {
      final UsageConstraint x;

      public NegationConstraint(UsageConstraint x) {
        this.x = x;
      }

      @Override
      public boolean checkResidence(final DependencyContext.S residence) {
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
      public boolean checkResidence(final DependencyContext.S residence) {
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
    final boolean privacy = ((m.access & Opcodes.ACC_PRIVATE) > 0) && !c.name.equals(scope.name);
    final boolean packageLocality = isPackageLocal(m.access) && !c.getPackageName().equals(scope.getPackageName());

    return !privacy && !packageLocality;
  }

  private boolean empty(final DependencyContext.S s) {
    return s.equals(myContext.get(""));
  }

  private Collection<DependencyContext.S> getAllSubclasses(final DependencyContext.S root) {
    final Collection<DependencyContext.S> result = new HashSet<DependencyContext.S>();

    addAllSubclasses(root, result);

    return result;
  }

  private void addAllSubclasses(final DependencyContext.S root, final Collection<DependencyContext.S> acc) {
    final Collection<DependencyContext.S> directSubclasses = myClassToSubclasses.get(root);

    acc.add(root);

    if (directSubclasses != null) {
      for (final DependencyContext.S s : directSubclasses) {
        if (acc.contains(s)) {
          continue;
        }

        addAllSubclasses(s, acc);
      }
    }
  }

  public boolean differentiate(final Mappings delta,
                               final Collection<String> removed,
                               final Collection<File> filesToCompile,
                               final Collection<File> compiledFiles,
                               final Collection<File> affectedFiles) {
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

    for (DependencyContext.S fileName : delta.mySourceFileToClasses.keyCollection()) {
      final Set<ClassRepr> classes = (Set<ClassRepr>)delta.mySourceFileToClasses.get(fileName);
      final Set<ClassRepr> pastClasses = (Set<ClassRepr>)mySourceFileToClasses.get(fileName);
      final Set<DependencyContext.S> dependants = new HashSet<DependencyContext.S>();

      self.appendDependents(pastClasses, dependants);

      final Set<UsageRepr.Usage> affectedUsages = new HashSet<UsageRepr.Usage>();
      final Set<UsageRepr.AnnotationUsage> annotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
      final Map<UsageRepr.Usage, Util.UsageConstraint> usageConstraints = new HashMap<UsageRepr.Usage, Util.UsageConstraint>();

      final Difference.Specifier<ClassRepr> classDiff = Difference.make(pastClasses, classes);

      debug("Processing changed classes");

      for (Pair<ClassRepr, Difference> changed : classDiff.changed()) {
        final ClassRepr it = changed.first;
        final ClassRepr.Diff diff = (ClassRepr.Diff)changed.second;

        debug("Changed: ");
        debug(it.name);

        final int addedModifiers = diff.addedModifiers();
        final int removedModifiers = diff.removedModifiers();

        final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) > 0;
        final boolean interfacesChanged = !diff.interfaces().unchanged();
        final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) > 0;

        if (superClassChanged || interfacesChanged || signatureChanged) {
          final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
          final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

          u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, extendsChanged || interfacesRemoved || signatureChanged);
        }

        if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) > 0 || (diff.removedModifiers() & Opcodes.ACC_INTERFACE) > 0) {
          affectedUsages.add(it.createUsage());
        }

        if (it.isAnnotation() && it.policy == RetentionPolicy.SOURCE) {
          debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
          return false;
        }

        if ((addedModifiers & Opcodes.ACC_PROTECTED) > 0) {
          final UsageRepr.Usage usage = it.createUsage();

          affectedUsages.add(usage);
          usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
        }

        if (diff.packageLocalOn()) {
          final UsageRepr.Usage usage = it.createUsage();

          affectedUsages.add(usage);
          usageConstraints.put(usage, u.new PackageConstraint(it.getPackageName()));
        }

        if ((addedModifiers & Opcodes.ACC_FINAL) > 0 || (addedModifiers & Opcodes.ACC_PRIVATE) > 0) {
          affectedUsages.add(it.createUsage());
        }

        if ((addedModifiers & Opcodes.ACC_ABSTRACT) > 0) {
          affectedUsages.add(UsageRepr.createClassNewUsage(myContext, it.name));
        }

        if ((addedModifiers & Opcodes.ACC_STATIC) > 0 ||
            (removedModifiers & Opcodes.ACC_STATIC) > 0 ||
            (addedModifiers & Opcodes.ACC_ABSTRACT) > 0) {
          affectedUsages.add(UsageRepr.createClassNewUsage(myContext, it.name));
        }

        if (it.isAnnotation()) {
          if (diff.retentionChanged()) {
            affectedUsages.add(it.createUsage());
          }
          else {
            final Collection<ElementType> removedtargets = diff.targets().removed();

            if (removedtargets.contains(ElementType.LOCAL_VARIABLE)) {
              debug("Annotation, removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
              return false;
            }

            if (!removedtargets.isEmpty()) {
              annotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr
                .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), null, removedtargets));
            }

            for (MethodRepr m : diff.methods().added()) {
              if (!m.hasValue()) {
                affectedUsages.add(it.createUsage());
              }
            }
          }
        }

        for (MethodRepr m : diff.methods().added()) {
          if (it.isAnnotation()) {
            continue;
          }

          if ((it.access & Opcodes.ACC_INTERFACE) > 0 ||
              (it.access & Opcodes.ACC_ABSTRACT) > 0 ||
              (m.access & Opcodes.ACC_ABSTRACT) > 0) {
            u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
          }

          Collection<DependencyContext.S> propagated = null;

          if ((m.access & Opcodes.ACC_PRIVATE) == 0 && !myContext.getValue(m.name).equals("<init>")) {
            final ClassRepr oldIt = getReprByName(it.name);

            if (oldIt != null && self.findOverridenMethods(m, oldIt).size() > 0) {

            }
            else {
              propagated = u.propagateMethodAccess(m.name, it.name);
              u.affectMethodUsages(m, propagated, m.createMetaUsage(myContext, it.name), affectedUsages, dependants);
            }
          }

          if ((m.access & Opcodes.ACC_PRIVATE) == 0) {
            final Collection<Pair<MethodRepr, ClassRepr>> affectedMethods = u.findAllMethodsBySpecificity(m, it);
            final MethodRepr.Predicate overrides = MethodRepr.equalByJavaRules(m);

            if (propagated == null) {
              propagated = u.propagateMethodAccess(m.name, it.name);
            }

            final Collection<MethodRepr> lessSpecific = it.findMethods(u.lessSpecific(m));

            for (MethodRepr mm : lessSpecific) {
              if (!mm.equals(m)) {
                u.affectMethodUsages(mm, propagated, mm.createUsage(myContext, it.name), affectedUsages, dependants);
              }
            }

            for (Pair<MethodRepr, ClassRepr> p : affectedMethods) {
              final MethodRepr mm = p.first;
              final ClassRepr cc = p.second;

              if (cc == myMockClass) {

              }
              else {
                if (overrides.satisfy(mm)) {
                  final Option<Boolean> subtypeOf = u.isSubtypeOf(mm.type, m.type);

                  if (weakerAccess(mm.access, m.access) ||
                      ((m.access & Opcodes.ACC_STATIC) > 0 && (mm.access & Opcodes.ACC_STATIC) == 0) ||
                      ((m.access & Opcodes.ACC_STATIC) == 0 && (mm.access & Opcodes.ACC_STATIC) > 0) ||
                      ((m.access & Opcodes.ACC_FINAL) > 0) ||
                      !m.exceptions.equals(mm.exceptions) ||
                      (subtypeOf.isNone() || !subtypeOf.value()) ||
                      !empty(mm.signature) || !empty(m.signature)) {
                    final DependencyContext.S file = myClassToSourceFile.get(cc.name);

                    if (file != null) {
                      affectedFiles.add(new File(myContext.getValue(file)));
                    }
                  }
                }
                else {
                  final Collection<DependencyContext.S> yetPropagated = self.propagateMethodAccess(mm.name, cc.name);
                  final Collection<DependencyContext.S> deps = myClassToClassDependency.get(cc.name);

                  if (deps != null) {
                    dependants.addAll(deps);
                  }

                  u.affectMethodUsages(mm, yetPropagated, mm.createUsage(myContext, cc.name), affectedUsages, dependants);
                }
              }
            }

            final Collection<DependencyContext.S> subClasses = getAllSubclasses(it.name);

            if (subClasses != null) {
              for (final DependencyContext.S subClass : subClasses) {
                final ClassRepr r = u.reprByName(subClass);
                final DependencyContext.S sourceFileName = myClassToSourceFile.get(subClass);

                if (r != null && sourceFileName != null) {
                  final DependencyContext.S outerClass = r.outerClassName;

                  if (u.methodVisible(outerClass, m)) {
                    affectedFiles.add(new File(myContext.getValue(sourceFileName)));
                  }
                }
              }
            }
          }
        }

        for (MethodRepr m : diff.methods().removed()) {
          final Collection<Pair<MethodRepr, ClassRepr>> overridenMethods = u.findOverridenMethods(m, it);
          final Collection<DependencyContext.S> propagated = u.propagateMethodAccess(m.name, it.name);

          if (overridenMethods.size() == 0) {
            u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), affectedUsages, dependants);
          }
          else {
            boolean clear = true;

            loop:
            for (Pair<MethodRepr, ClassRepr> overriden : overridenMethods) {
              final MethodRepr mm = overriden.first;

              if (mm == myMockMethod || !mm.type.equals(m.type) || !empty(mm.signature) || !empty(m.signature)) {
                clear = false;
                break loop;
              }
            }

            if (!clear) {
              u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), affectedUsages, dependants);
            }
          }

          if ((m.access & Opcodes.ACC_ABSTRACT) == 0) {
            for (DependencyContext.S p : propagated) {
              final ClassRepr s = u.reprByName(p);

              if (s != null) {
                final Collection<Pair<MethodRepr, ClassRepr>> overridenInS = u.findOverridenMethods(m, s);

                overridenInS.addAll(overridenMethods);

                boolean allAbstract = true;
                boolean visited = false;

                for (Pair<MethodRepr, ClassRepr> pp : overridenInS) {
                  final ClassRepr cc = pp.second;

                  if (cc == myMockClass) {
                    visited = true;
                    continue;
                  }

                  if (cc.name.equals(it.name)) {
                    continue;
                  }

                  visited = true;
                  allAbstract = ((pp.first.access & Opcodes.ACC_ABSTRACT) > 0) || ((cc.access & Opcodes.ACC_INTERFACE) > 0);

                  if (!allAbstract) {
                    break;
                  }
                }

                if (allAbstract && visited) {
                  final DependencyContext.S source = myClassToSourceFile.get(p);

                  if (source != null) {
                    affectedFiles.add(new File(myContext.getValue(source)));
                  }
                }
              }
            }
          }
        }

        for (Pair<MethodRepr, Difference> mr : diff.methods().changed()) {
          final MethodRepr m = mr.first;
          final MethodRepr.Diff d = (MethodRepr.Diff)mr.second;
          final boolean throwsChanged = (d.exceptions().added().size() > 0) || (d.exceptions().changed().size() > 0);

          if (it.isAnnotation()) {
            if (d.defaultRemoved()) {
              final List<DependencyContext.S> l = new LinkedList<DependencyContext.S>();
              l.add(m.name);
              annotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr
                .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.name), l, null));
            }
          }
          else if (d.base() != Difference.NONE || throwsChanged) {
            final Collection<DependencyContext.S> propagated = u.propagateMethodAccess(m.name, it.name);

            boolean affected = false;
            boolean constrained = false;

            final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

            if (d.packageLocalOn()) {
              u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);

              for (UsageRepr.Usage usage : usages) {
                usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
              }

              affectedUsages.addAll(usages);
              affected = true;
              constrained = true;
            }

            if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
              if (!affected) {
                u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                affectedUsages.addAll(usages);
              }
            }
            else if ((d.base() & Difference.ACCESS) > 0) {
              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
                if (!affected) {
                  u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                  affectedUsages.addAll(usages);
                }

                if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0) {
                  u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                }
              }
              else {
                if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0 ||
                    (d.addedModifiers() & Opcodes.ACC_PUBLIC) > 0 ||
                    (d.addedModifiers() & Opcodes.ACC_ABSTRACT) > 0) {
                  u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                }

                if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && !((d.removedModifiers() & Opcodes.ACC_PRIVATE) > 0)) {
                  if (!constrained) {
                    if (!affected) {
                      u.affectMethodUsages(m, propagated, m.createUsage(myContext, it.name), usages, dependants);
                      affectedUsages.addAll(usages);
                    }

                    for (UsageRepr.Usage usage : usages) {
                      usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                    }
                  }
                }
              }
            }
          }
        }

        final int mask = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

        for (FieldRepr f : diff.fields().added()) {
          final boolean fPrivate = (f.access & Opcodes.ACC_PRIVATE) > 0;
          final boolean fProtected = (f.access & Opcodes.ACC_PROTECTED) > 0;
          final boolean fPublic = (f.access & Opcodes.ACC_PUBLIC) > 0;
          final boolean fPLocal = !fPrivate && !fProtected && !fPublic;

          if (!fPrivate) {
            final Collection<DependencyContext.S> subClasses = getAllSubclasses(it.name);

            if (subClasses != null) {
              for (final DependencyContext.S subClass : subClasses) {
                final ClassRepr r = u.reprByName(subClass);
                final DependencyContext.S sourceFileName = myClassToSourceFile.get(subClass);

                if (r != null && sourceFileName != null) {
                  if (r.isLocal) {
                    affectedFiles.add(new File(myContext.getValue(sourceFileName)));
                  }
                  else {
                    final DependencyContext.S outerClass = r.outerClassName;

                    if (!empty(outerClass) && u.fieldVisible(outerClass, f)) {
                      affectedFiles.add(new File(myContext.getValue(sourceFileName)));
                    }
                  }
                }

                final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(f.name, subClass);
                u.affectFieldUsages(f, propagated, f.createUsage(myContext, subClass), affectedUsages, dependants);

                final Collection<DependencyContext.S> deps = myClassToClassDependency.get(subClass);

                if (deps != null) {
                  dependants.addAll(deps);
                }
              }
            }
          }

          final Collection<Pair<FieldRepr, ClassRepr>> overridden = u.findOverridenFields(f, it);

          for (Pair<FieldRepr, ClassRepr> p : overridden) {
            final FieldRepr ff = p.first;
            final ClassRepr cc = p.second;

            final boolean ffPrivate = (ff.access & Opcodes.ACC_PRIVATE) > 0;
            final boolean ffProtected = (ff.access & Opcodes.ACC_PROTECTED) > 0;
            final boolean ffPublic = (ff.access & Opcodes.ACC_PUBLIC) > 0;
            final boolean ffPLocal = isPackageLocal(ff.access);

            if (!ffPrivate) {
              final Collection<DependencyContext.S> propagated = o.propagateFieldAccess(ff.name, cc.name);
              final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

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

                for (UsageRepr.Usage usage : localUsages) {
                  usageConstraints.put(usage, constaint);
                }
              }

              affectedUsages.addAll(localUsages);
            }
          }
        }

        debug("Processing removed fields");

        for (FieldRepr f : diff.fields().removed()) {
          debug("Field ");
          debug(f.name);

          if ((f.access & Opcodes.ACC_PRIVATE) == 0 && (f.access & mask) == mask && f.hasValue()) {
            debug("Field had value and was (non-private) final static => a switch to non-incremental mode requested");
            return false;
          }

          final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(f.name, it.name);
          u.affectFieldUsages(f, propagated, f.createUsage(myContext, it.name), affectedUsages, dependants);
        }

        debug("Processing changed fields");

        for (Pair<FieldRepr, Difference> f : diff.fields().changed()) {
          final Difference d = f.second;
          final FieldRepr field = f.first;

          debug("Field ");
          debug(field.name);

          if ((field.access & Opcodes.ACC_PRIVATE) == 0 && (field.access & mask) == mask) {
            if ((d.base() & Difference.ACCESS) > 0 || (d.base() & Difference.VALUE) > 0) {
              debug("Inline field changed it's access or value => a switch to non-incremental mode requested");
              return false;
            }
          }

          if (d.base() != Difference.NONE) {
            final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(field.name, it.name);
            final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

            boolean affected = false;

            if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
              u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), usages, dependants);
              affectedUsages.addAll(usages);
              affected = true;
            }
            else if ((d.base() & Difference.ACCESS) > 0) {
              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
                if (!affected) {
                  u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), usages, dependants);
                  affectedUsages.addAll(usages);
                  affected = true;
                }
              }
              else {
                if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                  if (!affected) {
                    u.affectFieldUsages(field, propagated, field.createAssignUsage(myContext, it.name), usages, dependants);
                    affectedUsages.addAll(usages);
                    affected = true;
                  }
                }

                if ((d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                  if (!affected) {
                    u.affectFieldUsages(field, propagated, field.createUsage(myContext, it.name), usages, dependants);
                    affectedUsages.addAll(usages);
                  }

                  for (UsageRepr.Usage usage : usages) {
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
      }

      for (ClassRepr c : classDiff.removed()) {
        affectedUsages.add(c.createUsage());
      }

      for (ClassRepr c : classDiff.added()) {
        final Collection<DependencyContext.S> depClasses = myClassToClassDependency.get(c.name);

        if (depClasses != null) {
          for (DependencyContext.S depClass : depClasses) {
            final DependencyContext.S fName = myClassToSourceFile.get(depClass);

            if (fName != null) {
              affectedFiles.add(new File(myContext.getValue(fName)));
            }
          }
        }
      }

      if (dependants != null) {
        final Set<DependencyContext.S> dependentFiles = new HashSet<DependencyContext.S>();

        for (DependencyContext.S depClass : dependants) {
          final DependencyContext.S file = myClassToSourceFile.get(depClass);

          if (file != null) {
            dependentFiles.add(file);
          }
        }

        filewise:
        for (DependencyContext.S depFile : dependentFiles) {
          final File theFile = new File(myContext.getValue(depFile));

          if (affectedFiles.contains(theFile) || compiledFiles.contains(theFile)) {
            continue filewise;
          }

          final Collection<UsageRepr.Cluster> depClusters = mySourceFileToUsages.get(depFile);

          for (UsageRepr.Cluster depCluster : depClusters) {
            final Set<UsageRepr.Usage> depUsages = depCluster.getUsages();

            if (depUsages != null) {
              final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>(depUsages);

              usages.retainAll(affectedUsages);

              if (!usages.isEmpty()) {
                for (UsageRepr.Usage usage : usages) {
                  final Util.UsageConstraint constraint = usageConstraints.get(usage);

                  if (constraint == null) {
                    affectedFiles.add(theFile);
                    continue filewise;
                  }
                  else {
                    final Set<DependencyContext.S> residenceClasses = depCluster.getResidence(usage);
                    for (DependencyContext.S residentName : residenceClasses) {
                      if (constraint.checkResidence(residentName)) {
                        affectedFiles.add(theFile);
                        continue filewise;
                      }
                    }

                  }
                }
              }

              if (annotationQuery.size() > 0) {
                final Collection<UsageRepr.Usage> annotationUsages = mySourceFileToAnnotationUsages.get(depFile);

                for (UsageRepr.Usage usage : annotationUsages) {
                  for (UsageRepr.AnnotationUsage query : annotationQuery) {
                    if (query.satisfies(usage)) {
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
    }

    return true;
  }

  public void integrate(final Mappings delta, final Collection<File> compiled, final Collection<String> removed) {
    try {
      if (removed != null) {
        for (String file : removed) {
          final DependencyContext.S key = myContext.get(file);
          final Set<ClassRepr> classes = (Set<ClassRepr>)mySourceFileToClasses.get(key);
          final Collection<UsageRepr.Cluster> clusters = mySourceFileToUsages.get(key);

          if (classes != null) {
            for (ClassRepr cr : classes) {
              myClassToSubclasses.remove(cr.name);
              myClassToSourceFile.remove(cr.name);
              myClassToClassDependency.remove(cr.name);

              for (DependencyContext.S superSomething : cr.getSupers()) {
                myClassToSubclasses.removeFrom(superSomething, cr.name);
              }

              for (UsageRepr.Cluster cluster : clusters) {
                final Set<UsageRepr.Usage> usages = cluster.getUsages();
                if (usages != null) {
                  for (UsageRepr.Usage u : usages) {
                    if (u instanceof UsageRepr.ClassUsage) {
                      final Set<DependencyContext.S> residents = cluster.getResidence(u);

                      if (residents != null && residents.contains(cr.name)) {
                        myClassToClassDependency.removeFrom(((UsageRepr.ClassUsage)u).className, cr.name);
                      }
                    }
                  }
                }
              }
            }
          }

          mySourceFileToClasses.remove(key);
          mySourceFileToUsages.remove(key);
        }
      }

      myClassToSubclasses.putAll(delta.myClassToSubclasses);
      mySourceFileToClasses.putAll(delta.mySourceFileToClasses);
      mySourceFileToUsages.putAll(delta.mySourceFileToUsages);
      mySourceFileToAnnotationUsages.putAll(delta.mySourceFileToAnnotationUsages);
      myClassToSourceFile.putAll(delta.myClassToSourceFile);

      for (DependencyContext.S file : delta.myClassToClassDependency.keyCollection()) {
        final Collection<DependencyContext.S> now = delta.myClassToClassDependency.get(file);
        final Collection<DependencyContext.S> past = myClassToClassDependency.get(file);

        if (past == null) {
          myClassToClassDependency.put(file, now);
        }
        else {
          final Collection<DependencyContext.S> removeSet = new HashSet<DependencyContext.S>();

          for (File c : compiled) {
            removeSet.add(myContext.get(FileUtil.toSystemIndependentName(c.getAbsolutePath())));
          }

          removeSet.removeAll(now);

          past.addAll(now);
          past.removeAll(removeSet);

          myClassToClassDependency.remove(file);
          myClassToClassDependency.put(file, past);
        }
      }
    }
    finally {
      delta.close();
    }
  }

  public Callbacks.Backend getCallback() {
    return new Callbacks.Backend() {
      public Collection<String> getClassFiles() {
        final HashSet<String> result = new HashSet<String>();

        for (DependencyContext.S s : myClassToSourceFile.keyCollection()) {
          result.add(myContext.getValue(s));
        }

        return result;
      }

      public void associate(final String classFileName, final Callbacks.SourceFileNameLookup sourceFileName, final ClassReader cr) {
        final DependencyContext.S classFileNameS = myContext.get(classFileName);
        final Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> result =
          new ClassfileAnalyzer(myContext).analyze(classFileNameS, cr);
        final ClassRepr repr = result.first;
        final UsageRepr.Cluster localUsages = result.second.first;
        final Set<UsageRepr.Usage> localAnnotationUsages = result.second.second;

        final String srcFileName = sourceFileName.get(repr == null ? null : myContext.getValue(repr.getSourceFileName()));
        final DependencyContext.S sourceFileNameS = myContext.get(srcFileName);

        if (repr != null) {
          final DependencyContext.S className = repr.name;

          myClassToSourceFile.put(repr.name, sourceFileNameS);
          mySourceFileToClasses.put(sourceFileNameS, repr);

          for (DependencyContext.S s : repr.getSupers()) {
            myClassToSubclasses.put(s, repr.name);
          }

          for (UsageRepr.Usage u : localUsages.getUsages()) {
            final DependencyContext.S owner = u.getOwner();

            if (!owner.equals(className)) {
              final DependencyContext.S sourceFile = repr.getSourceFileName();
              final DependencyContext.S ownerSourceFile = myClassToSourceFile.get(owner);

              if (ownerSourceFile != null) {
                if (!ownerSourceFile.equals(sourceFile)) {
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
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    return (Set<ClassRepr>)mySourceFileToClasses.get(myContext.get(sourceFileName));
  }

  public void close() {
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

  public void flush() {
    myClassToSubclasses.flush();
    myClassToClassDependency.flush();
    mySourceFileToClasses.flush();
    mySourceFileToAnnotationUsages.flush();
    mySourceFileToUsages.flush();
    myClassToSourceFile.flush();

    if (!myIsDelta) {
      // flush if you own the context
      final DependencyContext context = myContext;
      if (context != null) {
        context.flush();
      }
    }
  }
}
