package org.jetbrains.ether.dependencyView;

import com.sun.tools.javac.util.Pair;
import org.jetbrains.ether.ProjectWrapper;
import org.objectweb.asm.ClassReader;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 16:20
 * To change this template use File | Settings | File Templates.
 */
public class SourceToClass {
    private Map<String, Set<ClassRepr>> mapping = new HashMap<String, Set<ClassRepr>>();
    private Map<String, Set<Usage>> usages = new HashMap<String, Set<Usage>>();

    public void inherit(final SourceToClass c) {
        mapping = c.mapping;
        usages = c.usages;
    }

    public Callbacks.Backend getCallback() {
        return new Callbacks.Backend() {
            private final Set<String> affected = new HashSet<String>();

            public Set<String> getAffectedFiles() {
                return affected;
            }

            public void begin() {
                affected.clear();
            }

            public void end() {

            }

            public void associate(String classFileName, String sourceFileName, ClassReader cr) {
                classFileName = project.getRelativePath(classFileName);
                sourceFileName = project.getRelativePath(sourceFileName);

                Set<ClassRepr> classes = mapping.get(sourceFileName);

                final Pair<ClassRepr, Set<Usage>> result = ClassfileAnalyzer.analyze(classFileName, cr);
                final ClassRepr repr = result.fst;
                final Set<Usage> localUsages = result.snd;

                if (repr != null) {
                    if (classes == null) {
                        classes = new HashSet<ClassRepr>();
                        classes.add(repr);
                        mapping.put(sourceFileName, classes);
                    } else {
                        if (affected.contains(sourceFileName)) {
                            classes.add(repr);
                        } else {
                            classes.clear();
                            classes.add(repr);
                            affected.add(sourceFileName);
                        }
                    }
                }

                Set<Usage> u = usages.get(sourceFileName);

                if (u == null) {
                    u = new HashSet<Usage> ();
                }

                u.addAll(localUsages);
                usages.put(sourceFileName, u);

                //System.out.println("Filename: " + sourceFileName + ", number of usages: " + u.size());
                //System.out.flush();
            }

            public void associate(final Set<ClassRepr> classes, final Set<Usage> u, final String sourceFileName) {
                mapping.put(sourceFileName, classes);
                usages.put(sourceFileName, u);
            }
        };
    }

    private final ProjectWrapper project;

    public SourceToClass(final ProjectWrapper p) {
        project = p;
    }

    public void clear() {
        mapping.clear();
    }

    public void clearClasses(final String sourceFileName) {
        final Set<ClassRepr> classes = mapping.get(sourceFileName);

        if (classes != null)
            classes.clear();
    }

    public Set<ClassRepr> getClasses(final String sourceFileName) {
        return mapping.get(sourceFileName);
    }

    public Set<Usage> getUsages(final String sourceFileName) {
        return usages.get(sourceFileName);
    }
}
