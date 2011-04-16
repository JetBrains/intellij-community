package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;
import org.jetbrains.ether.ProjectWrapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.*;
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

    private FoxyMap<StringCache.S, ClassRepr> sourceFileToClasses = new FoxyMap<StringCache.S, ClassRepr>(classSetConstructor);
    private FoxyMap<StringCache.S, UsageRepr.Usage> sourceFileToUsages = new FoxyMap<StringCache.S, UsageRepr.Usage>(usageSetConstructor);
    private Map<StringCache.S, StringCache.S> classToSourceFile = new HashMap<StringCache.S, StringCache.S>();
    private FoxyMap<StringCache.S, StringCache.S> fileToFileDependency = new FoxyMap<StringCache.S, StringCache.S>(stringSetConstructor);
    private FoxyMap<StringCache.S, StringCache.S> waitingForResolve = new FoxyMap<StringCache.S, StringCache.S>(stringSetConstructor);

    private void affectAll(final StringCache.S fileName, final Set<StringCache.S> affectedFiles) {
        final Set<StringCache.S> dependants = (Set<StringCache.S>) fileToFileDependency.foxyGet(fileName);

        if (dependants != null) {
            affectedFiles.addAll(dependants);
        }
    }

    public boolean differentiate(final Mappings delta, final Set<StringCache.S> removed, final Set<StringCache.S> compiledFiles, final Set<StringCache.S> affectedFiles) {
        if (removed != null) {
            for (StringCache.S file : removed) {
                affectAll(file, affectedFiles);
            }
        }

        for (StringCache.S fileName : delta.sourceFileToClasses.keySet()) {
            final Set<ClassRepr> classes = (Set<ClassRepr>) delta.sourceFileToClasses.foxyGet(fileName);
            final Set<ClassRepr> pastClasses = (Set<ClassRepr>) sourceFileToClasses.foxyGet(fileName);
            final Set<StringCache.S> dependants = (Set<StringCache.S>) fileToFileDependency.foxyGet(fileName);
            final Set<UsageRepr.Usage> affectedUsages = new HashSet<UsageRepr.Usage>();

            final Difference.Specifier<ClassRepr> classDiff = Difference.make(pastClasses, classes);

            for (Pair<ClassRepr, Difference> changed : classDiff.changed()) {
                final ClassRepr it = changed.fst;
                final ClassRepr.Diff diff = (ClassRepr.Diff) changed.snd;

                if (diff.base() != Difference.NONE || !diff.interfaces().unchanged() || !diff.nestedClasses().unchanged()) {
                    affectedUsages.add(it.createUsage());
                }

                for (MethodRepr m : diff.methods().removed()) {
                    affectedUsages.add(m.createUsage(it.name));
                }

                for (Pair<MethodRepr, Difference> m : diff.methods().changed()) {
                    affectedUsages.add(m.fst.createUsage(it.name));
                }

                for (FieldRepr f : diff.fields().removed()) {
                    affectedUsages.add(f.createUsage(it.name));
                }

                for (Pair<FieldRepr, Difference> f : diff.fields().changed()) {
                    final Difference d = f.snd;
                    final FieldRepr field = f.fst;

                    final int mask = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

                    if (((field.access & Opcodes.ACC_PUBLIC) > 0 || (field.access & Opcodes.ACC_PROTECTED) > 0) && ((field.access & mask) == mask)) {
                        if ((d.base() & Difference.ACCESS) > 0 || (d.base() & Difference.VALUE) > 0) {
                            return false;
                        }
                    }

                    affectedUsages.add(field.createUsage(it.name));
                }
            }

            for (ClassRepr c : classDiff.removed()) {
                affectedUsages.add(c.createUsage());
            }

            if (dependants != null) {
                dependants.removeAll(compiledFiles);

                for (StringCache.S depFile : dependants) {
                    final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>(sourceFileToUsages.foxyGet(depFile));

                    usages.retainAll(affectedUsages);

                    if (!usages.isEmpty()) {
                        affectedFiles.add(depFile);
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

        sourceFileToClasses.putAll(delta.sourceFileToClasses);
        sourceFileToUsages.putAll(delta.sourceFileToUsages);
        classToSourceFile.putAll(delta.classToSourceFile);
        fileToFileDependency.putAll(delta.fileToFileDependency);
    }

    private void updateSourceToUsages(final StringCache.S source, final Set<UsageRepr.Usage> usages) {
        sourceFileToUsages.put(source, usages);
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
            public void associate(final String classFileName, final Callbacks.SourceFileNameLookup sourceFileName, final ClassReader cr) {
                final StringCache.S classFileNameS = StringCache.get(project.getRelativePath(classFileName));
                final Pair<ClassRepr, Set<UsageRepr.Usage>> result = ClassfileAnalyzer.analyze(classFileNameS, cr);
                final ClassRepr repr = result.fst;
                final Set<UsageRepr.Usage> localUsages = result.snd;

                final StringCache.S sourceFileNameS =
                        StringCache.get(project.getRelativePath(sourceFileName.get(repr == null ? null : repr.getSourceFileName ().value)));

                for (UsageRepr.Usage u : localUsages) {
                    updateDependency(sourceFileNameS, u.getOwner());
                }

                if (repr != null) {
                    updateClassToSource(repr.name, sourceFileNameS);
                    updateSourceToClasses(sourceFileNameS, repr);
                }

                if (!localUsages.isEmpty()) {
                    updateSourceToUsages(sourceFileNameS, localUsages);
                }
            }

            public void associate(final Set<ClassRepr> classes, final Set<UsageRepr.Usage> usages, final String sourceFileName) {
                final StringCache.S sourceFileNameS = StringCache.get(sourceFileName);

                sourceFileToClasses.put(sourceFileNameS, classes);
                sourceFileToUsages.put(sourceFileNameS, usages);

                for (ClassRepr r : classes) {
                    updateClassToSource(r.name, sourceFileNameS);
                }

                for (UsageRepr.Usage u : usages) {
                    updateDependency(sourceFileNameS, u.getOwner());
                }
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

    public Set<ClassRepr> getClasses(final String sourceFileName) {
        return (Set<ClassRepr>) sourceFileToClasses.foxyGet(StringCache.get(sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final String sourceFileName) {
        return (Set<UsageRepr.Usage>) sourceFileToUsages.foxyGet(StringCache.get(sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final StringCache.S sourceFileName) {
        return (Set<UsageRepr.Usage>) sourceFileToUsages.foxyGet(sourceFileName);
    }

    public void print() {
        try {
            final BufferedWriter w = new BufferedWriter(new FileWriter("dep.txt"));
            for (StringCache.S key : fileToFileDependency.keySet()) {
                final Set<StringCache.S> value = (Set<StringCache.S>) fileToFileDependency.foxyGet(key);

                w.write(key.value + " -->");
                w.newLine();

                for (StringCache.S s : value) {
                    if (s == null)
                        w.write("  <null>");
                    else
                        w.write("  " + s.value);

                    w.newLine();
                }
            }

            w.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
