package org.jetbrains.ether;

import com.sun.tools.javac.util.Pair;
import org.jetbrains.ether.DirectoryScanner;
import org.jetbrains.ether.ModuleHistory;
import org.jetbrains.ether.ProjectHistory;
import org.jetbrains.jps.*;
import org.jetbrains.jps.resolvers.PathEntry;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 18.11.10
 * Time: 19:57
 * To change this template use File | Settings | File Templates.
 */
public class HistoryCollector {
    private static Pair<Long, Long> myDefaultPair = new Pair<Long, Long> (Long.MAX_VALUE, 0l);

    private static Pair<Long, Long> join (final Pair<Long, Long> a, final Pair<Long, Long> b) {
        if (a == null)
            return b;

        if (b == null)
            return a;

        return new Pair<Long, Long> (Math.min(a.fst, b.fst), Math.max(a.snd, b.snd));
    }

    private static String[] mySourceExts = {".java", ".groovy"};
    private static String[] myClassExts = {".class"};

    private static Comparator<Library> myLibraryComparator = new Comparator<Library>() {
            public int compare (Library a, Library b) {
                return a.getName().compareTo(b.getName());
            }
        };

    private static Comparator<Module> myModuleComparator = new Comparator<Module>() {
            public int compare (Module a, Module b) {
                return a.getName().compareTo(b.getName());
            }
        };

    private static <T> List<T> prepare (final Collection<T> coll, final Comparator<T> comp) {
        List<T> list = new ArrayList<T> ();

        for (T elem : coll) {
          if (elem != null) {
              list.add(elem);
          }
        }

        Collections.sort(list, comp);

        return list;
    }

    private static <T extends Comparable<? super T>> List<T> prepare (final Collection<T> coll) {
        return prepare(coll, new Comparator<T> () {
            public int compare (T a, T b) {
                return a.compareTo(b);
            }
        });
    }

    private static void listToBuffer (StringBuffer buf, final List list) {
        for (Object o : prepare (list)) {
            if (o instanceof String) {
                buf.append(o + "\n");
            }
            else {
                buf.append("*** <" + o.getClass().getName() + "> is not String ***\n");
            }
        }
    }

    private static void namedListToBuffer (StringBuffer buf, final String name, final List list) {
        buf.append(name + ":\n");
        listToBuffer(buf, list);
    }

    private static Pair<Long, Long> directoryToBuffer (StringBuffer buf, final String dir, final String[] exts) {
        if (dir != null) {
            final DirectoryScanner.Result result = DirectoryScanner.getFiles(dir, exts);

            for (String name : prepare (result.myFiles)) {
                buf.append(name + "\n");
            }

            return new Pair<Long, Long> (result.myEarliest, result.myLatest);
        }

        return myDefaultPair;
    }

    private static Pair<Long, Long> sourceRootToBuffer (StringBuffer buf, final String name, final List<String> dir) {
        Pair<Long, Long> result = myDefaultPair;

        buf.append(name + ":\n");

        for (String d : prepare (dir)) {
            if (dir != null) {
                buf.append(d + ":\n");
                result = join (result, directoryToBuffer(buf, d, mySourceExts));
            }
        }

        return result;
    }

    private static void classPathItemToBuffer (StringBuffer buf, final ClasspathItem cpi, boolean all) {
        final ClasspathKind[] allKinds = {ClasspathKind.PRODUCTION_COMPILE, ClasspathKind.PRODUCTION_RUNTIME, ClasspathKind.TEST_COMPILE, ClasspathKind.TEST_RUNTIME};
        final ClasspathKind[] oneKind = {ClasspathKind.PRODUCTION_COMPILE};
        final ClasspathKind[] kinds = all ? allKinds : oneKind;

        for (int i=0; i<kinds.length; i++) {
            final ClasspathKind kind = kinds[i];
            final String name = kind.name();

            namedListToBuffer(buf, "classpath" + (all ? " (" + name + ")" : ""), cpi.getClasspathRoots(kind));
        }
    }

    public static void libraryToBuffer (StringBuffer buf, final Library library) {
        library.forceInit();
        buf.append ("Library: " + library.getName() + "\n");
        classPathItemToBuffer(buf, library, false);
    }

    public static ModuleHistory moduleToBuffer (StringBuffer buf, final Module module) {
        buf.append("Module: " + module.getName() + "\n");

        classPathItemToBuffer(buf, module, true);
        namedListToBuffer(buf, "Excludes", module.getExcludes());

        buf.append("Libraries:\n");
        for (Library lib : prepare (module.getLibraries().values(), myLibraryComparator)) {
            libraryToBuffer(buf, lib);
        }

        long ss = sourceRootToBuffer(buf, "SourceRoots", module.getSourceRoots()).snd;
        buf.append("OutputPath: " + module.getOutputPath() + "\n");
        long os = directoryToBuffer(buf, module.getOutputPath(), myClassExts).fst;

        long tss = sourceRootToBuffer(buf, "TestRoots", module.getTestRoots()).snd;
        buf.append("TestOutputPath: " + module.getTestOutputPath() + "\n");
        long tos = directoryToBuffer(buf, module.getTestOutputPath(), myClassExts).fst;

        buf.append("Dependencies:\n");
        for (Module.ModuleDependency dep : module.getDependencies()){
            final ClasspathItem item = dep.getItem();
            if (item instanceof Module) {
                buf.append("module " + ((Module) item).getName() + "\n");
            }
            else if (item instanceof Library) {
                buf.append("library " + ((Library) item).getName() + "\n");
            }
            else if (item instanceof JavaSdk) {
                buf.append("javaSdk " + ((JavaSdk) item).getName() + "\n");
            }
            else if (item instanceof Sdk) {
                buf.append("Sdk " + ((Sdk) item).getName() + "\n");
            }
            else if (item instanceof PathEntry) {
                buf.append("pathEntry " + ((PathEntry) item).getPath() + "\n");
            }
            else {
                buf.append("unknown ClasspathItem implementation in dependencies: <" + item.getClass().getName() + ">\n");
            }
        }

        return new ModuleHistory(module.getName(), ss, os, tss, tos);
    }

    public static ProjectHistory collectHistory (final Project prj) {
        StringBuffer buf = new StringBuffer();
        Map<String, ModuleHistory> moduleHistories = new HashMap<String, ModuleHistory> ();

        for (Library lib : prepare (prj.getLibraries().values(), myLibraryComparator)) {
            libraryToBuffer(buf, lib);
        }

        for (Module mod : prepare (prj.getModules().values(), myModuleComparator)) {
            moduleHistories.put(mod.getName(), moduleToBuffer(buf, mod));
        }

        return new ProjectHistory(buf.toString(), moduleHistories);
    }
}
