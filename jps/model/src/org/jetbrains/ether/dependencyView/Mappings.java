package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ether.RW;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
  private final static String classToSubclassesName = "classToSubclasses.tab";
  private final static String classToClassName = "classToClass.tab";
  private final static String sourceToClassName = "sourceToClass.tab";
  private final static String sourceToAnnotationsName = "sourceToAnnotations.tab";
  private final static String sourceToUsagesName = "sourceToUsages.tab";
  private final static String classToSourceName = "classToSource.tab";

  private final DependencyContext context;

  private static TransientMultiMaplet.CollectionConstructor<ClassRepr> classSetConstructor = new TransientMultiMaplet.CollectionConstructor<ClassRepr>() {
    public Set<ClassRepr> create() {
      return new HashSet<ClassRepr>();
    }
  };

  private static TransientMultiMaplet.CollectionConstructor<UsageRepr.Usage> usageSetConstructor = new TransientMultiMaplet.CollectionConstructor<UsageRepr.Usage>() {
    public Set<UsageRepr.Usage> create() {
      return new HashSet<UsageRepr.Usage>();
    }
  };

  private static TransientMultiMaplet.CollectionConstructor<DependencyContext.S> stringSetConstructor =
    new TransientMultiMaplet.CollectionConstructor<DependencyContext.S>() {
      public Set<DependencyContext.S> create() {
        return new HashSet<DependencyContext.S>();
      }
    };

  private final MultiMaplet<DependencyContext.S, DependencyContext.S> classToSubclasses;
  private final MultiMaplet<DependencyContext.S, DependencyContext.S> classToClassDependency;

  private final MultiMaplet<DependencyContext.S, ClassRepr> sourceFileToClasses;
  private final MultiMaplet<DependencyContext.S, UsageRepr.Usage> sourceFileToAnnotationUsages;

  private final Maplet<DependencyContext.S, UsageRepr.Cluster> sourceFileToUsages;
  private final Maplet<DependencyContext.S, DependencyContext.S> classToSourceFile;

  private Mappings(final DependencyContext context) {
    this.context = context;

    classToSubclasses = new TransientMultiMaplet<DependencyContext.S, DependencyContext.S>(stringSetConstructor);
    sourceFileToClasses = new TransientMultiMaplet<DependencyContext.S, ClassRepr>(classSetConstructor);
    sourceFileToUsages = new TransientMaplet<DependencyContext.S, UsageRepr.Cluster>();
    sourceFileToAnnotationUsages = new TransientMultiMaplet<DependencyContext.S, UsageRepr.Usage>(usageSetConstructor);
    classToSourceFile = new TransientMaplet<DependencyContext.S, DependencyContext.S>();
    classToClassDependency = new TransientMultiMaplet<DependencyContext.S, DependencyContext.S>(stringSetConstructor);
  }

  public Mappings(final File rootDir) throws IOException {
    context = new DependencyContext(rootDir);

    classToSubclasses = new PersistentMultiMaplet<DependencyContext.S, DependencyContext.S>(
      DependencyContext.getTableFile (rootDir, classToSubclassesName),
      DependencyContext.descriptorS,
      DependencyContext.descriptorS,
      stringSetConstructor
    );

    classToClassDependency = new PersistentMultiMaplet<DependencyContext.S, DependencyContext.S>(
      DependencyContext.getTableFile (rootDir, classToClassName),
      DependencyContext.descriptorS,
      DependencyContext.descriptorS,
      stringSetConstructor
    );

    sourceFileToClasses = new PersistentMultiMaplet<DependencyContext.S, ClassRepr>(
      DependencyContext.getTableFile (rootDir, sourceToClassName),
      DependencyContext.descriptorS,
      ClassRepr.externalizer(context),
      classSetConstructor
    );

    sourceFileToAnnotationUsages = new PersistentMultiMaplet<DependencyContext.S, UsageRepr.Usage>(
      DependencyContext.getTableFile(rootDir, sourceToAnnotationsName), 
      DependencyContext.descriptorS, 
      UsageRepr.externalizer(context), 
      usageSetConstructor
    );    
    
    sourceFileToUsages = new PersistentMaplet<DependencyContext.S, UsageRepr.Cluster>(
      DependencyContext.getTableFile(rootDir, sourceToUsagesName),
      DependencyContext.descriptorS,
      UsageRepr.Cluster.clusterExternalizer(context)
    );
    
    classToSourceFile = new PersistentMaplet<DependencyContext.S, DependencyContext.S>(
      DependencyContext.getTableFile(rootDir, classToSourceName),
      DependencyContext.descriptorS,
      DependencyContext.descriptorS
    );
  }

  public Mappings createDelta(){
    return new Mappings(context);
  }

  private void compensateRemovedContent(final Collection<File> compiled) {
    for (File file : compiled) {
      final DependencyContext.S key = context.get(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
      if (!sourceFileToClasses.containsKey(key)) {
        sourceFileToClasses.put(key, new HashSet<ClassRepr>());
      }
    }
  }

  @Nullable
  private ClassRepr getReprByName(final DependencyContext.S name) {
    final DependencyContext.S source = classToSourceFile.get(name);

    if (source != null) {
      final Collection<ClassRepr> reprs = sourceFileToClasses.get(source);

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

  private class Util {
    final Mappings delta;

    private Util() {
      delta = null;
    }

    private Util(Mappings delta) {
      this.delta = delta;
    }

    void appendDependents(final Set<ClassRepr> classes, final Set<DependencyContext.S> result) {
      if (classes == null) {
        return;
      }

      for (ClassRepr c : classes) {
        final Collection<DependencyContext.S> depClasses = delta.classToClassDependency.get(c.name);

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

        final Collection<DependencyContext.S> subclasses = classToSubclasses.get(reflcass);

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

    Collection<Pair<MethodRepr, ClassRepr>> findOverridenMethods(final MethodRepr m, final ClassRepr c) {
      final Set<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();

      new Object() {
        public void run(final ClassRepr c) {
          final DependencyContext.S[] supers = c.getSupers();

          for (DependencyContext.S succName : supers) {
            final ClassRepr r = reprByName(succName);

            if (r != null) {
              boolean cont = true;

              if (r.methods.contains(m)) {
                final MethodRepr mm = r.findMethod(m);

                if (mm != null) {
                  if ((mm.access & Opcodes.ACC_PRIVATE) == 0) {
                    result.add(new Pair<MethodRepr, ClassRepr>(mm, r));
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
                  if ((ff.access & Opcodes.ACC_PRIVATE) == 0) {
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
      if (delta != null) {
        final ClassRepr r = delta.getReprByName(name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(name);
    }

    boolean isInheritorOf(final DependencyContext.S who, final DependencyContext.S whom) {
      if (who.equals(whom)) {
        return true;
      }

      final ClassRepr repr = reprByName(who);

      if (repr != null) {
        for (DependencyContext.S s : repr.getSupers()) {
          if (isInheritorOf(s, whom)) {
            return true;
          }
        }
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

      return false;
    }

    void affectSubclasses(final DependencyContext.S className,
                          final Collection<File> affectedFiles,
                          final Collection<UsageRepr.Usage> affectedUsages,
                          final Collection<DependencyContext.S> dependants,
                          final boolean usages) {
      final DependencyContext.S fileName = classToSourceFile.get(className);

      if (fileName == null) {
        return;
      }

      if (usages) {
        final ClassRepr classRepr = reprByName(className);

        if (classRepr != null) {
          affectedUsages.add(classRepr.createUsage());
        }
      }

      final Collection<DependencyContext.S> depClasses = classToClassDependency.get(fileName);

      if (depClasses != null) {
        dependants.addAll(depClasses);
      }

      affectedFiles.add(new File(context.getValue(fileName)));

      final Collection<DependencyContext.S> directSubclasses = classToSubclasses.get(className);

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
        final Collection<DependencyContext.S> deps = classToClassDependency.get(p);

        if (deps != null) {
          dependents.addAll(deps);
        }

        affectedUsages
          .add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(context, p) : field.createUsage(context, p));
      }
    }

    void affectMethodUsages(final MethodRepr method,
                            final Collection<DependencyContext.S> subclasses,
                            final UsageRepr.Usage rootUsage,
                            final Set<UsageRepr.Usage> affectedUsages,
                            final Set<DependencyContext.S> dependents) {
      affectedUsages.add(rootUsage);

      for (DependencyContext.S p : subclasses) {
        final Collection<DependencyContext.S> deps = classToClassDependency.get(p);

        if (deps != null) {
          dependents.addAll(deps);
        }

        affectedUsages.add(method.createUsage(context, p));
      }
    }

    void affectAll(final DependencyContext.S className, final Collection<File> affectedFiles) {
      final Set<DependencyContext.S> dependants = (Set<DependencyContext.S>)classToClassDependency.get(className);

      if (dependants != null) {
        for (DependencyContext.S depClass : dependants) {
          final DependencyContext.S depFile = classToSourceFile.get(depClass);
          if (depFile != null) {
            affectedFiles.add(new File(context.getValue(depFile)));
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
        return !ClassRepr.getPackageName(context.getValue(residence)).equals(packageName);
      }
    }

    public class InheritanceConstraint extends UsageConstraint {
      public final DependencyContext.S rootClass;

      public InheritanceConstraint(final DependencyContext.S rootClass) {
        this.rootClass = rootClass;
      }

      @Override
      public boolean checkResidence(final DependencyContext.S residence) {
        return !isInheritorOf(residence, rootClass);
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
        final Collection<ClassRepr> classes = sourceFileToClasses.get(context.get(file));

        if (classes != null) {
          for (ClassRepr c : classes) {
            u.affectAll(c.name, affectedFiles);
          }
        }
      }
    }

    for (DependencyContext.S fileName : delta.sourceFileToClasses.keyCollection()) {
      final Set<ClassRepr> classes = (Set<ClassRepr>)delta.sourceFileToClasses.get(fileName);
      final Set<ClassRepr> pastClasses = (Set<ClassRepr>)sourceFileToClasses.get(fileName);
      final Set<DependencyContext.S> dependants = new HashSet<DependencyContext.S>();

      self.appendDependents(pastClasses, dependants);

      final Set<UsageRepr.Usage> affectedUsages = new HashSet<UsageRepr.Usage>();
      final Set<UsageRepr.AnnotationUsage> annotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
      final Map<UsageRepr.Usage, Util.UsageConstraint> usageConstraints = new HashMap<UsageRepr.Usage, Util.UsageConstraint>();

      final Difference.Specifier<ClassRepr> classDiff = Difference.make(pastClasses, classes);

      for (Pair<ClassRepr, Difference> changed : classDiff.changed()) {
        final ClassRepr it = changed.first;
        final ClassRepr.Diff diff = (ClassRepr.Diff)changed.second;

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
          affectedUsages.add(UsageRepr.createClassNewUsage(context, it.name));
        }

        if ((addedModifiers & Opcodes.ACC_STATIC) > 0 ||
            (removedModifiers & Opcodes.ACC_STATIC) > 0 ||
            (addedModifiers & Opcodes.ACC_ABSTRACT) > 0) {
          affectedUsages.add(UsageRepr.createClassNewUsage(context, it.name));
        }

        if (it.isAnnotation()) {
          if (diff.retentionChanged()) {
            affectedUsages.add(it.createUsage());
          }
          else {
            final Collection<ElementType> removedtargets = diff.targets().removed();

            if (removedtargets.contains(ElementType.LOCAL_VARIABLE)) {
              return false;
            }

            if (!removedtargets.isEmpty()) {
              annotationQuery
                .add((UsageRepr.AnnotationUsage)UsageRepr.createAnnotationUsage(context, TypeRepr.createClassType(context, it.name), null, removedtargets));
            }

            for (MethodRepr m : diff.methods().added()) {
              if (!m.hasValue()) {
                affectedUsages.add(it.createUsage());
              }
            }
          }
        }

        for (MethodRepr m : diff.methods().added()) {
          if ((it.access & Opcodes.ACC_INTERFACE) > 0 || (m.access & Opcodes.ACC_ABSTRACT) > 0) {
            u.affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
          }
        }

        for (MethodRepr m : diff.methods().removed()) {
          final Collection<Pair<MethodRepr, ClassRepr>> overridenMethods = u.findOverridenMethods(m, it);
          final Collection<DependencyContext.S> propagated = u.propagateMethodAccess(m.name, it.name);

          if (overridenMethods.size() == 0) {
            u.affectMethodUsages(m, propagated, m.createUsage(context, it.name), affectedUsages, dependants);
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
                  if (pp.second.name.equals(it.name)) {
                    continue;
                  }

                  visited = true;
                  allAbstract = ((pp.first.access & Opcodes.ACC_ABSTRACT) > 0) || ((pp.second.access & Opcodes.ACC_INTERFACE) > 0);

                  if (!allAbstract) {
                    break;
                  }
                }

                if (allAbstract && visited) {
                  final DependencyContext.S source = classToSourceFile.get(p);

                  if (source != null) {
                    affectedFiles.add(new File(context.getValue(source)));
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
              annotationQuery.add((UsageRepr.AnnotationUsage)UsageRepr.createAnnotationUsage(context, TypeRepr.createClassType(context, it.name), l, null));
            }
          }
          else if (d.base() != Difference.NONE || throwsChanged) {
            if (d.packageLocalOn()) {
              final UsageRepr.Usage usage = m.createUsage(context, it.name);

              affectedUsages.add(usage);
              usageConstraints.put(usage, u.new PackageConstraint(it.getPackageName()));
            }

            final Collection<DependencyContext.S> propagated = u.propagateMethodAccess(m.name, it.name);

            if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
              u.affectMethodUsages(m, propagated, m.createUsage(context, it.name), affectedUsages, dependants);
            }
            else if ((d.base() & Difference.ACCESS) > 0) {
              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
                u.affectMethodUsages(m, propagated, m.createUsage(context, it.name), affectedUsages, dependants);

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
                  final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();
                  u.affectMethodUsages(m, propagated, m.createUsage(context, it.name), usages, dependants);

                  for (UsageRepr.Usage usage : usages) {
                    usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                  }

                  affectedUsages.addAll(usages);
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
            final Collection<DependencyContext.S> subClasses = classToSubclasses.get(it.name);

            if (subClasses != null) {
              for (final DependencyContext.S subClass : subClasses) {
                final ClassRepr r = u.reprByName(subClass);
                final DependencyContext.S sourceFileName = classToSourceFile.get(subClass);

                if (r != null && sourceFileName != null) {
                  if (r.isLocal) {
                    affectedFiles.add(new File(context.getValue(sourceFileName)));
                  }
                  else {
                    final DependencyContext.S outerClass = r.outerClassName;

                      if (u.fieldVisible(outerClass, f)) {
                        affectedFiles.add(new File(context.getValue(sourceFileName)));
                      }
                  }
                }

                final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(f.name, subClass);
                u.affectFieldUsages(f, propagated, f.createUsage(context, subClass), affectedUsages, dependants);

                final Collection<DependencyContext.S> deps = classToClassDependency.get(subClass);

                if (deps != null) {
                  dependants.addAll(deps);
                }
              }
            }
          }

          final Collection<Pair<FieldRepr, ClassRepr>> overriden = u.findOverridenFields(f, it);

          for (Pair<FieldRepr, ClassRepr> p : overriden) {
            final FieldRepr ff = p.first;
            final ClassRepr cc = p.second;

            final boolean ffPrivate = (ff.access & Opcodes.ACC_PRIVATE) > 0;
            final boolean ffProtected = (ff.access & Opcodes.ACC_PROTECTED) > 0;
            final boolean ffPublic = (ff.access & Opcodes.ACC_PUBLIC) > 0;
            final boolean ffPLocal = !ffPrivate && !ffProtected && !ffPublic;

            if (!ffPrivate) {
              final Collection<DependencyContext.S> propagated = o.propagateFieldAccess(ff.name, cc.name);
              final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

              u.affectFieldUsages(ff, propagated, ff.createUsage(context, cc.name), localUsages, dependants);

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

        for (FieldRepr f : diff.fields().removed()) {
          if ((f.access & mask) == mask && f.hasValue()) {
            return false;
          }

          final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(f.name, it.name);
          u.affectFieldUsages(f, propagated, f.createUsage(context, it.name), affectedUsages, dependants);
        }

        for (Pair<FieldRepr, Difference> f : diff.fields().changed()) {
          final Difference d = f.second;
          final FieldRepr field = f.first;

          if ((field.access & mask) == mask) {
            if ((d.base() & Difference.ACCESS) > 0 || (d.base() & Difference.VALUE) > 0) {
              return false;
            }
          }

          if (d.base() != Difference.NONE) {
            final Collection<DependencyContext.S> propagated = u.propagateFieldAccess(field.name, it.name);

            if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
              u.affectFieldUsages(field, propagated, field.createUsage(context, it.name), affectedUsages, dependants);
            }
            else if ((d.base() & Difference.ACCESS) > 0) {
              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
                u.affectFieldUsages(field, propagated, field.createUsage(context, it.name), affectedUsages, dependants);
              }
              else {
                if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                  u.affectFieldUsages(field, propagated, field.createAssignUsage(context, it.name), affectedUsages, dependants);
                }

                if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && (d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                  final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();
                  u.affectFieldUsages(field, propagated, field.createUsage(context, it.name), usages, dependants);

                  for (UsageRepr.Usage usage : usages) {
                    usageConstraints.put(usage, u.new InheritanceConstraint(it.name));
                  }

                  affectedUsages.addAll(usages);
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
        final Collection<DependencyContext.S> depClasses = classToClassDependency.get(c.name);

        if (depClasses != null) {
          for (DependencyContext.S depClass : depClasses) {
            final DependencyContext.S fName = classToSourceFile.get(depClass);

            if (fName != null) {
              affectedFiles.add(new File(context.getValue(fName)));
            }
          }
        }
      }

      if (dependants != null) {
        final Set<DependencyContext.S> dependentFiles = new HashSet<DependencyContext.S>();

        for (DependencyContext.S depClass : dependants) {
          final DependencyContext.S file = classToSourceFile.get(depClass);

          if (file != null) {
            dependentFiles.add(file);
          }
        }

        dependentFiles.removeAll(compiledFiles);

        filewise:
        for (DependencyContext.S depFile : dependentFiles) {
          if (affectedFiles.contains(new File(context.getValue(depFile)))) {
            continue filewise;
          }

          final UsageRepr.Cluster depCluster = sourceFileToUsages.get(depFile);
          final Set<UsageRepr.Usage> depUsages = depCluster.getUsages();

          if (depUsages != null) {
            final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>(depUsages);

            usages.retainAll(affectedUsages);

            if (!usages.isEmpty()) {
              for (UsageRepr.Usage usage : usages) {
                final Util.UsageConstraint constraint = usageConstraints.get(usage);

                if (constraint == null) {
                  affectedFiles.add(new File(context.getValue(depFile)));
                  continue filewise;
                }
                else {
                  final Set<DependencyContext.S> residenceClasses = depCluster.getResidence(usage);
                  for (DependencyContext.S residentName : residenceClasses) {
                    if (constraint.checkResidence(residentName)) {
                      affectedFiles.add(new File(context.getValue(depFile)));
                      continue filewise;
                    }
                  }

                }
              }
            }

            if (annotationQuery.size() > 0) {
              final Collection<UsageRepr.Usage> annotationUsages = sourceFileToAnnotationUsages.get(depFile);

              for (UsageRepr.Usage usage : annotationUsages) {
                for (UsageRepr.AnnotationUsage query : annotationQuery) {
                  if (query.satisfies(usage)) {
                    affectedFiles.add(new File(context.getValue(depFile)));
                    continue filewise;
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
    if (removed != null) {
      for (String file : removed) {
        final DependencyContext.S key = context.get(file);
        final Set<ClassRepr> classes = (Set<ClassRepr>)sourceFileToClasses.get(key);
        final UsageRepr.Cluster cluster = sourceFileToUsages.get(key);
        final Set<UsageRepr.Usage> usages = cluster == null ? null : cluster.getUsages();

        if (classes != null) {
          for (ClassRepr cr : classes) {
            classToSubclasses.remove(cr.name);
            classToSourceFile.remove(cr.name);
            classToClassDependency.remove(cr.name);

            for (DependencyContext.S superSomething : cr.getSupers()) {
              classToSubclasses.removeFrom(superSomething, cr.name);
            }

            if (usages != null) {
              for (UsageRepr.Usage u : usages) {
                if (u instanceof UsageRepr.ClassUsage) {
                  final Set<DependencyContext.S> residents = cluster.getResidence(u);

                  if (residents != null && residents.contains(cr.name)) {
                    classToClassDependency.removeFrom(((UsageRepr.ClassUsage)u).className, cr.name);
                  }
                }
              }
            }
          }
        }

        sourceFileToClasses.remove(key);
        sourceFileToUsages.remove(key);
      }
    }

    classToSubclasses.putAll(delta.classToSubclasses);
    sourceFileToClasses.putAll(delta.sourceFileToClasses);
    sourceFileToUsages.putAll(delta.sourceFileToUsages);
    sourceFileToAnnotationUsages.putAll(delta.sourceFileToAnnotationUsages);
    classToSourceFile.putAll(delta.classToSourceFile);

    for (DependencyContext.S file : delta.classToClassDependency.keyCollection()) {
      final Collection<DependencyContext.S> now = delta.classToClassDependency.get(file);
      final Collection<DependencyContext.S> past = classToClassDependency.get(file);

      if (past == null) {
        classToClassDependency.put(file, now);
      }
      else {
        final Collection<DependencyContext.S> removeSet = new HashSet<DependencyContext.S>();

        for (File c : compiled) {
          removeSet.add(context.get(FileUtil.toSystemIndependentName(c.getAbsolutePath())));
        }

        removeSet.removeAll(now);

        past.addAll(now);
        past.removeAll(removeSet);

        classToClassDependency.remove(file);
        classToClassDependency.put(file, past);
      }
    }
  }

  private void updateSourceToUsages(final DependencyContext.S source, final UsageRepr.Cluster usages) {
    final UsageRepr.Cluster c = sourceFileToUsages.get(source);

    if (c == null) {
      sourceFileToUsages.put(source, usages);
    }
    else {
      c.updateCluster(usages);
    }
  }

  private void updateSourceToAnnotationUsages(final DependencyContext.S source, final Set<UsageRepr.Usage> usages) {
    sourceFileToAnnotationUsages.put(source, usages);
  }

  public Callbacks.Backend getCallback() {
    return new Callbacks.Backend() {
      public Collection<String> getClassFiles() {
        final HashSet<String> result = new HashSet<String>();

        for (DependencyContext.S s : classToSourceFile.keyCollection()) {
          result.add(context.getValue(s));
        }

        return result;
      }

      public void associate(final String classFileName, final Callbacks.SourceFileNameLookup sourceFileName, final ClassReader cr) {
        final DependencyContext.S classFileNameS = context.get(classFileName);
        final Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> result =
          new ClassfileAnalyzer(context).analyze(classFileNameS, cr);
        final ClassRepr repr = result.first;
        final UsageRepr.Cluster localUsages = result.second.first;
        final Set<UsageRepr.Usage> localAnnotationUsages = result.second.second;

        final String srcFileName = sourceFileName.get(repr == null ? null : context.getValue(repr.getSourceFileName()));
        final DependencyContext.S sourceFileNameS = context.get(srcFileName);

        if (repr != null) {
          final DependencyContext.S className = repr.name;

          for (UsageRepr.Usage u : localUsages.getUsages()) {
            classToClassDependency.put(u.getOwner(), className);
          }
        }

        if (repr != null) {
          classToSourceFile.put(repr.name, sourceFileNameS);
          sourceFileToClasses.put(sourceFileNameS, repr);

          for (DependencyContext.S s : repr.getSupers()) {
            classToSubclasses.put(s, repr.name);
          }
        }

        if (!localUsages.isEmpty()) {
          updateSourceToUsages(sourceFileNameS, localUsages);
        }

        if (!localAnnotationUsages.isEmpty()) {
          updateSourceToAnnotationUsages(sourceFileNameS, localAnnotationUsages);
        }
      }
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    return (Set<ClassRepr>)sourceFileToClasses.get(context.get(sourceFileName));
  }

  public void close (){
    context.close();
    classToSubclasses.close ();
    classToClassDependency.close();
    sourceFileToClasses.close();
    sourceFileToAnnotationUsages.close();
    sourceFileToUsages.close();
    classToSourceFile.close();
  }
}
