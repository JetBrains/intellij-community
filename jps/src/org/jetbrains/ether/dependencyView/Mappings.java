package org.jetbrains.ether.dependencyView;

import org.codehaus.groovy.transform.DelegateASTTransformation;
import org.jetbrains.ether.Pair;
import org.jetbrains.ether.ProjectWrapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.BufferedWriter;
import java.io.FileWriter;
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

    private static FoxyMap.CollectionConstructor<ClassRepr> classSetConstructor = new FoxyMap.CollectionConstructor<ClassRepr>() {
        public Collection<ClassRepr> create() {
            return new HashSet<ClassRepr>();
        }
    };

    private static FoxyMap.CollectionConstructor<UsageRepr.Usage> usageSetConstructor = new FoxyMap.CollectionConstructor<UsageRepr.Usage>() {
        public Collection<UsageRepr.Usage> create() {
            return new HashSet<UsageRepr.Usage>();
        }
    };

    private static FoxyMap.CollectionConstructor<StringCache.S> stringSetConstructor = new FoxyMap.CollectionConstructor<StringCache.S>() {
        public Collection<StringCache.S> create() {
            return new HashSet<StringCache.S>();
        }
    };

    private FoxyMap<StringCache.S, StringCache.S> classToSubclasses = new FoxyMap<StringCache.S, StringCache.S>(stringSetConstructor);
    private FoxyMap<StringCache.S, ClassRepr> sourceFileToClasses = new FoxyMap<StringCache.S, ClassRepr>(classSetConstructor);
    private Map<StringCache.S, UsageRepr.Cluster> sourceFileToUsages = new HashMap<StringCache.S, UsageRepr.Cluster>();
    private FoxyMap<StringCache.S, UsageRepr.Usage> sourceFileToAnnotationUsages = new FoxyMap<StringCache.S, UsageRepr.Usage>(usageSetConstructor);
    private Map<StringCache.S, StringCache.S> classToSourceFile = new HashMap<StringCache.S, StringCache.S>();
    private FoxyMap<StringCache.S, StringCache.S> fileToFileDependency = new FoxyMap<StringCache.S, StringCache.S>(stringSetConstructor);
    private FoxyMap<StringCache.S, StringCache.S> waitingForResolve = new FoxyMap<StringCache.S, StringCache.S>(stringSetConstructor);
    private Map<StringCache.S, StringCache.S> formToClass = new HashMap<StringCache.S, StringCache.S>();
    private Map<StringCache.S, StringCache.S> classToForm = new HashMap<StringCache.S, StringCache.S>();

    public void compensateRemovedContent(final Collection<StringCache.S> compiled) {
        for (StringCache.S name : compiled) {
            final Collection<ClassRepr> classes = sourceFileToClasses.foxyGet(name);

            if (classes == null) {
                sourceFileToClasses.put(name, new HashSet<ClassRepr>());
            }
        }
    }

    private void propagateMemberAccessRec(final Collection<StringCache.S> acc, final boolean isField, final boolean root, final StringCache.S name, final StringCache.S reflcass) {
        final ClassRepr repr = reprByName(reflcass);

        if (repr != null) {
            if (!root) {
                final Collection members = isField ? repr.fields : repr.methods;

                for (Object o : members) {
                    final ProtoMember m = (ProtoMember) o;

                    if (m.name.equals(name)) {
                        return;
                    }
                }

                acc.add(reflcass);
            }

            final Collection<StringCache.S> subclasses = classToSubclasses.foxyGet(reflcass);

            if (subclasses != null) {
                for (StringCache.S subclass : subclasses) {
                    propagateMemberAccessRec(acc, isField, false, name, subclass);
                }
            }
        }
    }

    private Collection<StringCache.S> propagateMemberAccess(final boolean isField, final StringCache.S name, final StringCache.S className) {
        final Set<StringCache.S> acc = new HashSet<StringCache.S>();

        propagateMemberAccessRec(acc, isField, true, name, className);

        return acc;
    }

    private Collection<StringCache.S> propagateFieldAccess(final StringCache.S name, final StringCache.S className) {
        return propagateMemberAccess(true, name, className);
    }

    private Collection<StringCache.S> propagateMethodAccess(final StringCache.S name, final StringCache.S className) {
        return propagateMemberAccess(false, name, className);
    }

    private Collection<Pair<FieldRepr, ClassRepr>> findOverridenFields(final FieldRepr f, final ClassRepr c) {
        final Set<Pair<FieldRepr, ClassRepr>> result = new HashSet<Pair<FieldRepr, ClassRepr>>();

        new Object() {
            public void run(final ClassRepr c) {
                final StringCache.S[] supers = c.getSupers();

                for (StringCache.S succName : supers) {
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

    private ClassRepr reprByName(final StringCache.S name) {
        final Collection<ClassRepr> reprs = sourceFileToClasses.foxyGet(classToSourceFile.get(name));

        if (reprs != null) {
            for (ClassRepr repr : reprs) {
                if (repr.name.equals(name)) {
                    return repr;
                }
            }
        }

        return null;
    }

    private boolean isInheritorOf(final StringCache.S who, final StringCache.S whom) {
        if (who.equals(whom)) {
            return true;
        }

        final ClassRepr repr = reprByName(who);

        if (repr != null) {
            for (StringCache.S s : repr.getSupers()) {
                if (isInheritorOf(s, whom)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void affectSubclasses(final StringCache.S className, final Set<StringCache.S> affectedFiles, final Set<UsageRepr.Usage> affectedUsages, final Set<StringCache.S> dependants, final boolean usages) {
        final StringCache.S fileName = classToSourceFile.get(className);

        if (usages) {
            affectedUsages.add(reprByName(className).createUsage());
        }

        final Collection<StringCache.S> depFiles = fileToFileDependency.foxyGet(fileName);

        if (depFiles != null) {
            dependants.addAll(depFiles);
        }

        affectedFiles.add(fileName);

        final Collection<StringCache.S> directSubclasses = classToSubclasses.foxyGet(className);

        if (directSubclasses != null) {
            for (StringCache.S subClass : directSubclasses) {
                affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages);
            }
        }
    }

    private void affectFieldUsages(final FieldRepr field, final Collection<StringCache.S> subclasses, final UsageRepr.Usage rootUsage, final Set<UsageRepr.Usage> affectedUsages, final Set<StringCache.S> dependents) {
        affectedUsages.add(rootUsage);

        for (StringCache.S p : subclasses) {
            dependents.addAll(fileToFileDependency.foxyGet(classToSourceFile.get(p)));
            affectedUsages.add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(p) : field.createUsage(p));
        }
    }

    private void affectMethodUsages(final MethodRepr method, final Collection<StringCache.S> subclasses, final UsageRepr.Usage rootUsage, final Set<UsageRepr.Usage> affectedUsages, final Set<StringCache.S> dependents) {
        affectedUsages.add(rootUsage);

        for (StringCache.S p : subclasses) {
            dependents.addAll(fileToFileDependency.foxyGet(classToSourceFile.get(p)));
            affectedUsages.add(method.createUsage(p));
        }
    }

    private void affectAll(final StringCache.S fileName, final Set<StringCache.S> affectedFiles) {
        final Set<StringCache.S> dependants = (Set<StringCache.S>) fileToFileDependency.foxyGet(fileName);

        if (dependants != null) {
            affectedFiles.addAll(dependants);
        }
    }

    private abstract class UsageConstraint {
        public abstract boolean checkResidence(final StringCache.S residence);
    }

    private class PackageConstraint extends UsageConstraint {
        public final String packageName;

        public PackageConstraint(final String packageName) {
            this.packageName = packageName;
        }

        @Override
        public boolean checkResidence(final StringCache.S residence) {
            return !ClassRepr.getPackageName(residence).equals(packageName);
        }
    }

    private class InheritanceConstraint extends UsageConstraint {
        public final StringCache.S rootClass;

        public InheritanceConstraint(final StringCache.S rootClass) {
            this.rootClass = rootClass;
        }

        @Override
        public boolean checkResidence(final StringCache.S residence) {
            return !isInheritorOf(residence, rootClass);
        }
    }

    private class NegationConstraint extends UsageConstraint {
        final UsageConstraint x;

        public NegationConstraint(UsageConstraint x) {
            this.x = x;
        }

        @Override
        public boolean checkResidence(final StringCache.S residence) {
            return !x.checkResidence(residence);
        }
    }

    private class IntersectionConstraint extends UsageConstraint {
        final UsageConstraint x;
        final UsageConstraint y;

        public IntersectionConstraint(final UsageConstraint x, final UsageConstraint y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean checkResidence(final StringCache.S residence) {
            return x.checkResidence(residence) && y.checkResidence(residence);
        }
    }

    public boolean differentiate(final Mappings delta, final Set<StringCache.S> removed, final Set<StringCache.S> compiledFiles, final Set<StringCache.S> affectedFiles, final Set<StringCache.S> safeFiles) {
        if (removed != null) {
            for (StringCache.S file : removed) {
                affectAll(file, affectedFiles);
            }
        }

        for (StringCache.S fileName : delta.sourceFileToClasses.keySet()) {
            if (safeFiles.contains(fileName)) {
                continue;
            }

            final Set<ClassRepr> classes = (Set<ClassRepr>) delta.sourceFileToClasses.foxyGet(fileName);
            final Set<ClassRepr> pastClasses = (Set<ClassRepr>) sourceFileToClasses.foxyGet(fileName);
            final Set<StringCache.S> dependants = (Set<StringCache.S>) fileToFileDependency.foxyGet(fileName);
            final Set<UsageRepr.Usage> affectedUsages = new HashSet<UsageRepr.Usage>();
            final Set<UsageRepr.AnnotationUsage> annotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
            final Map<UsageRepr.Usage, UsageConstraint> usageConstraints = new HashMap<UsageRepr.Usage, UsageConstraint>();

            final Difference.Specifier<ClassRepr> classDiff = Difference.make(pastClasses, classes);

            for (Pair<ClassRepr, Difference> changed : classDiff.changed()) {
                final ClassRepr it = changed.fst;
                final ClassRepr.Diff diff = (ClassRepr.Diff) changed.snd;

                final int addedModifiers = diff.addedModifiers();
                final int removedModifiers = diff.removedModifiers();

                final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) > 0;
                final boolean interfacesChanged = !diff.interfaces().unchanged();
                final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) > 0;

                if (superClassChanged || interfacesChanged || signatureChanged) {
                    final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
                    final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

                    affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, extendsChanged || interfacesRemoved || signatureChanged);
                }

                if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) > 0 ||
                        (diff.removedModifiers() & Opcodes.ACC_INTERFACE) > 0) {
                    affectedUsages.add(it.createUsage());
                }

                if (it.isAnnotation() && it.policy == RetentionPolicy.SOURCE) {
                    return false;
                }

                if ((addedModifiers & Opcodes.ACC_PROTECTED) > 0) {
                    final UsageRepr.Usage usage = it.createUsage();

                    affectedUsages.add(usage);
                    usageConstraints.put(usage, new InheritanceConstraint(it.name));
                }

                if (diff.packageLocalOn()) {
                    final UsageRepr.Usage usage = it.createUsage();

                    affectedUsages.add(usage);
                    usageConstraints.put(usage, new PackageConstraint(it.getPackageName()));
                }

                if ((addedModifiers & Opcodes.ACC_FINAL) > 0 ||
                        (addedModifiers & Opcodes.ACC_PRIVATE) > 0) {
                    affectedUsages.add(it.createUsage());
                }

                if ((addedModifiers & Opcodes.ACC_ABSTRACT) > 0) {
                    affectedUsages.add(UsageRepr.createClassNewUsage(it.name));
                }

                if ((addedModifiers & Opcodes.ACC_STATIC) > 0 ||
                        (removedModifiers & Opcodes.ACC_STATIC) > 0 ||
                        (addedModifiers & Opcodes.ACC_ABSTRACT) > 0
                        ) {
                    affectedUsages.add(UsageRepr.createClassNewUsage(it.name));
                }

                if (it.isAnnotation()) {
                    if (diff.retentionChanged()) {
                        affectedUsages.add(it.createUsage());
                    } else {
                        final Collection<ElementType> removedtargets = diff.targets().removed();

                        if (removedtargets.contains(ElementType.LOCAL_VARIABLE)) {
                            return false;
                        }

                        if (!removedtargets.isEmpty()) {
                            annotationQuery.add((UsageRepr.AnnotationUsage) UsageRepr.createAnnotationUsage(TypeRepr.createClassType(it.name), null, removedtargets));
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
                        affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                    }
                }

                for (MethodRepr m : diff.methods().removed()) {
                    final Collection<StringCache.S> propagated = propagateMethodAccess(m.name, it.name);
                    affectMethodUsages(m, propagated, m.createUsage(it.name), affectedUsages, dependants);
                    affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                }

                for (Pair<MethodRepr, Difference> mr : diff.methods().changed()) {
                    final MethodRepr m = mr.fst;
                    final MethodRepr.Diff d = (MethodRepr.Diff) mr.snd;
                    final boolean throwsChanged = (d.exceptions().added().size() > 0) || (d.exceptions().changed().size() > 0);

                    if (it.isAnnotation()) {
                        if (d.defaultRemoved()) {
                            final List<StringCache.S> l = new LinkedList<StringCache.S>();
                            l.add(m.name);
                            annotationQuery.add((UsageRepr.AnnotationUsage) UsageRepr.createAnnotationUsage(TypeRepr.createClassType(it.name), l, null));
                        }
                    } else if (d.base() != Difference.NONE || throwsChanged) {
                        if (d.packageLocalOn()) {
                            final UsageRepr.Usage usage = m.createUsage(it.name);

                            affectedUsages.add(usage);
                            usageConstraints.put(usage, new PackageConstraint(it.getPackageName()));
                        }

                        final Collection<StringCache.S> propagated = propagateMethodAccess(m.name, it.name);

                        if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
                            affectMethodUsages(m, propagated, m.createUsage(it.name), affectedUsages, dependants);
                        } else if ((d.base() & Difference.ACCESS) > 0) {
                            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                                    (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                                    (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
                                affectMethodUsages(m, propagated, m.createUsage(it.name), affectedUsages, dependants);

                                if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0) {
                                    affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                                }
                            } else {
                                if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0 ||
                                        (d.addedModifiers() & Opcodes.ACC_PUBLIC) > 0 ||
                                        (d.addedModifiers() & Opcodes.ACC_ABSTRACT) > 0) {
                                    affectSubclasses(it.name, affectedFiles, affectedUsages, dependants, false);
                                }

                                if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && !((d.removedModifiers() & Opcodes.ACC_PRIVATE) > 0)) {
                                    final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();
                                    affectMethodUsages(m, propagated, m.createUsage(it.name), usages, dependants);

                                    for (UsageRepr.Usage u : usages) {
                                        usageConstraints.put(u, new InheritanceConstraint(it.name));
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
                        final Collection<Pair<FieldRepr, ClassRepr>> overriden = findOverridenFields(f, it);

                        for (Pair<FieldRepr, ClassRepr> p : overriden) {
                            final FieldRepr ff = p.fst;
                            final ClassRepr cc = p.snd;

                            final boolean ffPrivate = (ff.access & Opcodes.ACC_PRIVATE) > 0;
                            final boolean ffProtected = (ff.access & Opcodes.ACC_PROTECTED) > 0;
                            final boolean ffPublic = (ff.access & Opcodes.ACC_PUBLIC) > 0;
                            final boolean ffPLocal = !ffPrivate && !ffProtected && !ffPublic;

                            if (!ffPrivate) {
                                final Collection<StringCache.S> propagated = propagateFieldAccess(ff.name, cc.name);
                                final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

                                affectFieldUsages(ff, propagated, ff.createUsage(cc.name), localUsages, dependants);

                                if ((fPublic && (ffPublic || ffPLocal)) || (fProtected && ffProtected) || (fPLocal && ffPLocal)) {

                                } else {
                                    UsageConstraint constaint;

                                    if ((ffProtected && fPublic) || (fProtected && ffPublic) || (ffPLocal && fProtected)) {
                                        constaint = new NegationConstraint(new InheritanceConstraint(cc.name));
                                    } else if (ffPublic && ffPLocal) {
                                        constaint = new NegationConstraint(new PackageConstraint(cc.getPackageName()));
                                    } else {
                                        constaint = new IntersectionConstraint(
                                                new NegationConstraint(new InheritanceConstraint(cc.name)),
                                                new NegationConstraint(new PackageConstraint(cc.getPackageName()))
                                        );
                                    }

                                    for (UsageRepr.Usage u : localUsages) {
                                        usageConstraints.put(u, constaint);
                                    }
                                }

                                affectedUsages.addAll(localUsages);
                            }
                        }
                    }
                }

                for (FieldRepr f : diff.fields().removed()) {
                    if ((f.access & mask) == mask && f.hasValue()) {
                        return false;
                    }

                    final Collection<StringCache.S> propagated = propagateFieldAccess(f.name, it.name);
                    affectFieldUsages(f, propagated, f.createUsage(it.name), affectedUsages, dependants);
                }

                for (Pair<FieldRepr, Difference> f : diff.fields().changed()) {
                    final Difference d = f.snd;
                    final FieldRepr field = f.fst;

                    if ((field.access & mask) == mask) {
                        if ((d.base() & Difference.ACCESS) > 0 || (d.base() & Difference.VALUE) > 0) {
                            return false;
                        }
                    }

                    if (d.base() != Difference.NONE) {
                        final Collection<StringCache.S> propagated = propagateFieldAccess(field.name, it.name);

                        if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
                            affectFieldUsages(field, propagated, field.createUsage(it.name), affectedUsages, dependants);
                        } else if ((d.base() & Difference.ACCESS) > 0) {
                            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                                    (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                                    (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                                    (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
                                affectFieldUsages(field, propagated, field.createUsage(it.name), affectedUsages, dependants);
                            } else {
                                if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                                    affectFieldUsages(field, propagated, field.createAssignUsage(it.name), affectedUsages, dependants);
                                }

                                if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && (d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                                    final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();
                                    affectFieldUsages(field, propagated, field.createUsage(it.name), usages, dependants);

                                    for (UsageRepr.Usage u : usages) {
                                        usageConstraints.put(u, new InheritanceConstraint(it.name));
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

            if (dependants != null) {
                dependants.removeAll(compiledFiles);

                filewise:
                for (StringCache.S depFile : dependants) {
                    if (affectedFiles.contains(depFile)) {
                        continue filewise;
                    }

                    final UsageRepr.Cluster depCluster = sourceFileToUsages.get(depFile);
                    final Set<UsageRepr.Usage> depUsages = depCluster.getUsages();

                    if (depUsages != null) {
                        final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>(depUsages);

                        usages.retainAll(affectedUsages);

                        if (!usages.isEmpty()) {
                            for (UsageRepr.Usage usage : usages) {
                                final UsageConstraint constraint = usageConstraints.get(usage);

                                if (constraint == null) {
                                    affectedFiles.add(depFile);
                                    continue filewise;
                                } else {
                                    final Set<StringCache.S> residenceClasses = depCluster.getResidence(usage);
                                    for (StringCache.S residentName : residenceClasses) {
                                        if (constraint.checkResidence(residentName)) {
                                            affectedFiles.add(depFile);
                                            continue filewise;
                                        }
                                    }

                                }
                            }
                        }

                        if (annotationQuery.size() > 0) {
                            final Collection<UsageRepr.Usage> annotationUsages = sourceFileToAnnotationUsages.foxyGet(depFile);

                            for (UsageRepr.Usage usage : annotationUsages) {
                                for (UsageRepr.AnnotationUsage query : annotationQuery) {
                                    if (query.satisfies(usage)) {
                                        affectedFiles.add(depFile);
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

    public void integrate(final Mappings delta, final Set<StringCache.S> removed) {
        if (removed != null) {
            for (StringCache.S file : removed) {
                final Set<ClassRepr> classes = (Set<ClassRepr>) sourceFileToClasses.foxyGet(file);

                if (classes != null) {
                    for (ClassRepr cr : classes) {
                        classToSourceFile.remove(cr.fileName);
                    }
                }

                sourceFileToClasses.remove(file);
                sourceFileToUsages.remove(file);
                fileToFileDependency.remove(file);
            }
        }

        classToSubclasses.putAll(delta.classToSubclasses);
        formToClass.putAll(delta.formToClass);
        classToForm.putAll(delta.classToForm);
        sourceFileToClasses.putAll(delta.sourceFileToClasses);
        sourceFileToUsages.putAll(delta.sourceFileToUsages);
        sourceFileToAnnotationUsages.putAll(delta.sourceFileToAnnotationUsages);
        classToSourceFile.putAll(delta.classToSourceFile);
        fileToFileDependency.putAll(delta.fileToFileDependency);
    }

    private void updateFormToClass(final StringCache.S formName, final StringCache.S className) {
        formToClass.put(formName, className);
        classToForm.put(className, formName);
    }

    private void updateSourceToUsages(final StringCache.S source, final UsageRepr.Cluster usages) {
        final UsageRepr.Cluster c = sourceFileToUsages.get(source);

        if (c == null) {
            sourceFileToUsages.put(source, usages);
        } else {
            c.updateCluster(usages);
        }
    }

    private void updateSourceToAnnotationUsages(final StringCache.S source, final Set<UsageRepr.Usage> usages) {
        sourceFileToAnnotationUsages.put(source, usages);
    }

    private void updateSourceToClasses(final StringCache.S source, final ClassRepr classRepr) {
        sourceFileToClasses.put(source, classRepr);
    }

    private void updateDependency(final StringCache.S a, final StringCache.S owner) {
        final StringCache.S sourceFile = classToSourceFile.get(owner);

        if (sourceFile == null) {
            waitingForResolve.put(owner, a);
        } else {
            fileToFileDependency.put(sourceFile, a);
        }
    }

    private void updateClassToSource(final StringCache.S className, final StringCache.S sourceName) {
        classToSourceFile.put(className, sourceName);

        final Set<StringCache.S> waiting = (Set<StringCache.S>) waitingForResolve.foxyGet(className);

        if (waiting != null) {
            for (StringCache.S f : waiting) {
                updateDependency(f, className);
            }

            waitingForResolve.remove(className);
        }
    }

    public Callbacks.Backend getCallback() {
        return new Callbacks.Backend() {
            public Collection<StringCache.S> getClassFiles() {
                return classToSourceFile.keySet();
            }

            public void associate(final String classFileName, final Callbacks.SourceFileNameLookup sourceFileName, final ClassReader cr) {
                final StringCache.S classFileNameS = StringCache.get(project.getRelativePath(classFileName));
                final Pair<ClassRepr, Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>>> result = ClassfileAnalyzer.analyze(classFileNameS, cr);
                final ClassRepr repr = result.fst;
                final UsageRepr.Cluster localUsages = result.snd.fst;
                final Set<UsageRepr.Usage> localAnnotationUsages = result.snd.snd;

                final StringCache.S sourceFileNameS =
                        StringCache.get(project.getRelativePath(sourceFileName.get(repr == null ? null : repr.getSourceFileName().value)));

                for (UsageRepr.Usage u : localUsages.getUsages()) {
                    updateDependency(sourceFileNameS, u.getOwner());
                }

                if (repr != null) {
                    updateClassToSource(repr.name, sourceFileNameS);
                    updateSourceToClasses(sourceFileNameS, repr);

                    for (StringCache.S s : repr.getSupers()) {
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

            public void associate(final Set<Pair<ClassRepr, Set<StringCache.S>>> classes, final Pair<UsageRepr.Cluster, Set<UsageRepr.Usage>> usages, final String sourceFileName) {
                final StringCache.S sourceFileNameS = StringCache.get(sourceFileName);

                updateSourceToUsages(sourceFileNameS, usages.fst);
                sourceFileToAnnotationUsages.put(sourceFileNameS, usages.snd);

                for (Pair<ClassRepr, Set<StringCache.S>> c : classes) {
                    final ClassRepr r = c.fst;
                    final Set<StringCache.S> s = c.snd;
                    updateClassToSource(r.name, sourceFileNameS);
                    classToSubclasses.put(r.name, s);
                    sourceFileToClasses.put(sourceFileNameS, r);
                }

                for (UsageRepr.Usage u : usages.fst.getUsages()) {
                    updateDependency(sourceFileNameS, u.getOwner());
                }

                for (UsageRepr.Usage u : usages.snd) {
                    updateDependency(sourceFileNameS, u.getOwner());
                }
            }

            public void associateForm(StringCache.S formName, StringCache.S className) {
                updateFormToClass(formName, className);
            }
        };
    }

    private final ProjectWrapper project;

    public Mappings(final ProjectWrapper p) {
        project = p;
    }

    public Set<ClassRepr> getClasses(final StringCache.S sourceFileName) {
        return (Set<ClassRepr>) sourceFileToClasses.foxyGet(sourceFileName);
    }

    public Set<StringCache.S> getSubClasses(final StringCache.S className) {
        return (Set<StringCache.S>) classToSubclasses.foxyGet(className);
    }

    public UsageRepr.Cluster getUsages(final StringCache.S sourceFileName) {
        final UsageRepr.Cluster result = sourceFileToUsages.get(sourceFileName);

        if (result == null) {
            return new UsageRepr.Cluster();
        }

        return result;
    }

    public Set<UsageRepr.Usage> getAnnotationUsages(final StringCache.S sourceFileName) {
        return (Set<UsageRepr.Usage>) sourceFileToAnnotationUsages.foxyGet(sourceFileName);
    }

    public Set<StringCache.S> getFormClass(final StringCache.S formFileName) {
        final Set<StringCache.S> result = new HashSet<StringCache.S>();
        final StringCache.S name = formToClass.get(formFileName);

        if (name != null) {
            result.add(name);
        }

        return result;
    }

    public StringCache.S getJavaByForm(final StringCache.S formFileName) {
        final StringCache.S classFileName = formToClass.get(formFileName);
        return classToSourceFile.get(classFileName);
    }

    public StringCache.S getFormByJava(final StringCache.S javaFileName) {
        final Set<ClassRepr> classes = getClasses(javaFileName);

        if (classes != null) {
            for (ClassRepr c : classes) {
                final StringCache.S formName = classToForm.get(c.name);

                if (formName != null) {
                    return formName;
                }
            }
        }

        return null;
    }

    public void print() {
        try {
            final BufferedWriter w = new BufferedWriter(new FileWriter("dep.txt"));
            for (StringCache.S key : fileToFileDependency.keySet()) {
                final Set<StringCache.S> value = (Set<StringCache.S>) fileToFileDependency.foxyGet(key);

                w.write(key.value + " -->");
                w.newLine();

                if (value != null) {
                    for (StringCache.S s : value) {
                        if (s == null)
                            w.write("  <null>");
                        else
                            w.write("  " + s.value);

                        w.newLine();
                    }
                }
            }

            w.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
