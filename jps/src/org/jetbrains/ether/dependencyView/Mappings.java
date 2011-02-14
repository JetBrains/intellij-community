package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;
import org.jetbrains.ether.ProjectWrapper;
import org.objectweb.asm.ClassReader;

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
    private Map<StringCache.S, StringCache.S> classToSourceFile = new HashMap<StringCache.S, StringCache.S> ();
    private Map<StringCache.S, Set<StringCache.S>> fileToFileDependency = new HashMap<StringCache.S, Set<StringCache.S>> ();

    public void inherit(final Mappings c) {
        sourceFileToClasses = c.sourceFileToClasses;
        sourceFileToUsages = c.sourceFileToUsages;
        classToSourceFile = c.classToSourceFile;
        fileToFileDependency = c.fileToFileDependency;
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

    private void updateSourceToUsages (final StringCache.S source, final Set<UsageRepr.Usage> usages) {
        updateMap (sourceFileToUsages, source, usages);
    }

    private void updateSourceToClasses(final StringCache.S source, final ClassRepr classRepr) {
        updateMap(sourceFileToClasses, source, classRepr);
    }

    private void updateDependency(final StringCache.S a, final StringCache.S b) {
        updateMap (fileToFileDependency, a, b);
    }

    public Callbacks.Backend getCallback() {
        return new Callbacks.Backend() {
            private final Set<StringCache.S> affected = new HashSet<StringCache.S>();

            public Set<StringCache.S> getAffectedFiles() {
                return affected;
            }

            public void begin() {
                affected.clear();
            }

            public void end() {

            }

            public void associate(final String classFileName, final String sourceFileName, final ClassReader cr) {
                final StringCache.S classFileNameS = StringCache.get(project.getRelativePath(classFileName));
                final StringCache.S sourceFileNameS = StringCache.get(project.getRelativePath(sourceFileName));

                final Pair<ClassRepr, Set<UsageRepr.Usage>> result = ClassfileAnalyzer.analyze(classFileNameS, cr);
                final ClassRepr repr = result.fst;
                final Set<UsageRepr.Usage> localUsages = result.snd;

                for (UsageRepr.Usage u : localUsages) {
                    updateDependency(sourceFileNameS, classToSourceFile.get(u.owner));
                }

                if (repr != null) {
                    classToSourceFile.put(repr.name, sourceFileNameS);
                    updateSourceToClasses(sourceFileNameS, repr);
                }

                if (! localUsages.isEmpty()) {
                    updateSourceToUsages(sourceFileNameS, localUsages);
                }
            }

            public void associate(final Set<ClassRepr> classes, final Set<UsageRepr.Usage> usages, final String sourceFileName) {
                final StringCache.S sourceFileNameS = StringCache.get(sourceFileName);

                sourceFileToClasses.put(sourceFileNameS, classes);
                sourceFileToUsages.put(sourceFileNameS, usages);

                for (ClassRepr r : classes) {
                    classToSourceFile.put(r.name, sourceFileNameS);
                }

                for (UsageRepr.Usage u : usages) {
                    updateDependency(sourceFileNameS, classToSourceFile.get(u.owner));
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

    public Set<ClassRepr> getClasses (final String sourceFileName) {
        return sourceFileToClasses.get(StringCache.get(sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final String sourceFileName) {
        return sourceFileToUsages.get(StringCache.get (sourceFileName));
    }

    public Set<UsageRepr.Usage> getUsages(final StringCache.S sourceFileName) {
        return sourceFileToUsages.get(sourceFileName);
    }

    public Set<StringCache.S> getDependentFiles (final StringCache.S sourceFileName) {
        return fileToFileDependency.get(sourceFileName);
    }

    public Set<StringCache.S> getDependentFiles (final String sourceFileName) {
        return fileToFileDependency.get(StringCache.get(sourceFileName));
    }
}
