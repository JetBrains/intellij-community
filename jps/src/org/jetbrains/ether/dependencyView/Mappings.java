package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;
import org.jetbrains.ether.ProjectWrapper;
import org.objectweb.asm.ClassReader;

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

    private Map<StringCache.S, Set<ClassRepr>> sourceFileToClasses = new HashMap<StringCache.S, Set<ClassRepr>>();
    private Map<StringCache.S, Set<UsageRepr.Usage>> sourceFileToUsages = new HashMap<StringCache.S, Set<UsageRepr.Usage>>();
    private Map<StringCache.S, StringCache.S> classToSourceFile = new HashMap<StringCache.S, StringCache.S>();
    private Map<StringCache.S, Set<StringCache.S>> fileToFileDependency = new HashMap<StringCache.S, Set<StringCache.S>>();
    private Map<StringCache.S, Set<StringCache.S>> waitingForResolve = new HashMap<StringCache.S, Set<StringCache.S>>();

    public boolean differentiate(final Mappings delta, final Set<StringCache.S> removed, final Set<StringCache.S> affectedFiles) {
        boolean incremental = true;

        if (removed != null) {
            for (StringCache.S file : removed) {
                final Set<StringCache.S> dependants = fileToFileDependency.get(file);

                if (dependants != null) {
                    for (StringCache.S d : dependants) {
                        affectedFiles.add(d);
                    }
                }
            }
        }

        for (Map.Entry<StringCache.S, Set<ClassRepr>> e : delta.sourceFileToClasses.entrySet()) {
            final StringCache.S fileName = e.getKey();
            final Map<ClassRepr, ClassRepr> classes = new HashMap<ClassRepr, ClassRepr>();

            for (ClassRepr cr : e.getValue()) {
                classes.put(cr, cr);
            }

            final Set<ClassRepr> pastClasses = sourceFileToClasses.get(fileName);
            final Set<StringCache.S> dependants = fileToFileDependency.get(fileName);

            if (pastClasses != null) {
                for (ClassRepr past : pastClasses) {
                    final ClassRepr present = classes.get(past);

                    if (present != null) {
                        if (present.differentiate(past)) {
                            if (dependants != null)
                                for (StringCache.S d : dependants) {
                                    affectedFiles.add(d);
                                }
                        } else {
                            incremental = false;
                        }
                    }
                }
            }
        }

        return incremental;
    }

    public void integrate(final Mappings delta, final Set<StringCache.S> removed) {
        if (removed != null) {
            for (StringCache.S file : removed) {
                final Set<ClassRepr> classes = sourceFileToClasses.get(file);

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

    public class Depender {
        boolean resolved;
        StringCache.S value;

        private Depender(StringCache.S value) {
            this.resolved = false;
            this.value = value;
        }

        public StringCache.S getSourceFile() {
            if (!resolved) {
                value = classToSourceFile.get(value);
                resolved = true;
            }

            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Depender depender = (Depender) o;

            if (value != null ? !value.equals(depender.value) : depender.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }

    private <T> void updateMap(final Map<StringCache.S, Set<T>> map, final StringCache.S a, final T b) {
        Set<T> d = map.get(a);

        if (d == null) {
            d = new HashSet<T>();
            map.put(a, d);
        }

        d.add(b);
    }

    private <T> void updateMap(final Map<StringCache.S, Set<T>> map, final StringCache.S a, final Set<T> b) {
        final Set<T> d = map.get(a);

        if (d == null)
            map.put(a, b);
        else
            d.addAll(b);
    }

    private void updateSourceToUsages(final StringCache.S source, final Set<UsageRepr.Usage> usages) {
        updateMap(sourceFileToUsages, source, usages);
    }

    private void updateSourceToClasses(final StringCache.S source, final ClassRepr classRepr) {
        updateMap(sourceFileToClasses, source, classRepr);
    }

    private void updateDependency(final StringCache.S a, final StringCache.S owner) {
        final StringCache.S sourceFile = classToSourceFile.get(owner);

        if (sourceFile == null) {
            updateMap(waitingForResolve, owner, a);
        } else {
            updateMap(fileToFileDependency, sourceFile, a);
        }
    }

    private void updateClassToSource(final StringCache.S className, final StringCache.S sourceName) {
        classToSourceFile.put(className, sourceName);

        final Set<StringCache.S> waiting = waitingForResolve.get(className);

        if (waiting != null) {
            for (StringCache.S f : waiting) {
                updateDependency(f, className);
            }

            waitingForResolve.remove(className);
        }
    }

    public Callbacks.Backend getCallback() {
        return new Callbacks.Backend() {

            public void associate(final String classFileName, final String sourceFileName, final ClassReader cr) {
                final StringCache.S classFileNameS = StringCache.get(project.getRelativePath(classFileName));
                final StringCache.S sourceFileNameS = StringCache.get(project.getRelativePath(sourceFileName));

                final Pair<ClassRepr, Set<UsageRepr.Usage>> result = ClassfileAnalyzer.analyze(classFileNameS, cr);
                final ClassRepr repr = result.fst;
                final Set<UsageRepr.Usage> localUsages = result.snd;

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
        return sourceFileToClasses.get(sourceFileName);
    }

    public Set<ClassRepr> getClasses(final String sourceFileName) {
        return sourceFileToClasses.get(StringCache.get(sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final String sourceFileName) {
        return sourceFileToUsages.get(StringCache.get(sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final StringCache.S sourceFileName) {
        return sourceFileToUsages.get(sourceFileName);
    }

    public void print() {
        try {
            final BufferedWriter w = new BufferedWriter(new FileWriter("dep.txt"));
            for (Map.Entry<StringCache.S, Set<StringCache.S>> e : fileToFileDependency.entrySet()) {
                w.write(e.getKey().value + " -->");
                w.newLine();

                for (StringCache.S s : e.getValue()) {
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
