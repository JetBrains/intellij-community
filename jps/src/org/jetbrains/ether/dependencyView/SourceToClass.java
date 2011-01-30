package org.jetbrains.ether.dependencyView;

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
    private Map<String, Set<String>> mapping = new HashMap<String, Set<String>>();

    public void inherit (final SourceToClass c) {
        mapping = c.mapping;
    }

    public Callbacks.Backend getCallback () {
        return new Callbacks.Backend() {
            private final Set<String> affected = new HashSet<String> ();

            public void processClassfile(final String classFileName, final ClassReader c) {
                ClassfileAnalyzer.analyze(c);
            }

            public Set<String> getAffectedFiles () {
                return affected;
            }

            public void begin () {
                affected.clear();
            }

            public void end () {

            }

            public void associate(String classFileName, String sourceFileName) {
                classFileName = project.getRelativePath(classFileName);
                sourceFileName = project.getRelativePath(sourceFileName);

                Set<String> classes = mapping.get(sourceFileName);

                if (classes == null) {
                    classes = new HashSet<String> ();
                    classes.add(classFileName);
                    mapping.put(sourceFileName, classes);
                }
                else {
                    if (affected.contains(sourceFileName)) {
                        classes.add(classFileName);
                    }
                    else {
                        classes.clear();
                        classes.add(classFileName);
                        affected.add(sourceFileName);
                    }
                }
            }
        };
    }

    private final ProjectWrapper project;

    public SourceToClass (final ProjectWrapper p) {
        project = p;
    }

    public void clear () {
        mapping.clear();
    }

    public void clearClasses (final String sourceFileName) {
        final Set<String> classes = mapping.get(sourceFileName);

        if (classes != null)
            classes.clear();
    }

    public Set<String> getClasses (final String sourceFileName) {
        return mapping.get(sourceFileName);
    }

    public void println (final PrintStream s) {
        for (String source : mapping.keySet()) {
            s.println("Source: " + source);
            s.println("Classes: ");

            final Set<String> classes = mapping.get(source);

            if (classes != null) {
                for (String c : classes) {
                    s.println ("  " + c);
                }
            }
        }
    }
}
