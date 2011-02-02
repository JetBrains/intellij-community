package org.jetbrains.ether;

import org.codehaus.gant.GantBinding;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.ClassRepr;
import org.jetbrains.ether.dependencyView.SourceToClass;
import org.jetbrains.ether.dependencyView.Usage;
import org.jetbrains.jps.*;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.resolvers.PathEntry;

import java.io.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 2:58
 * To change this template use File | Settings | File Templates.
 */

public class ProjectWrapper {
    // Home directory
    private static final String myHomeDir = System.getProperty("user.home");

    // JPS directory
    private static final String myJPSDir = ".jps";

    // IDEA project structure directory name
    private static final String myIDEADir = ".idea";

    // JPS directory initialization
    private static void initJPSDirectory() {
        final File f = new File(myHomeDir + File.separator + myJPSDir);

        if (!f.exists())
            f.mkdir();
    }

    // File separator replacement
    private static final char myFileSeparatorReplacement = '.';

    // Original JPS Project
    private final Project myProject;

    // Project directory
    private final String myRoot;

    // Project snapshot file name
    private final String myProjectSnapshot;

    public interface ClasspathItemWrapper extends RW.Writable {
        public List<String> getClassPath(ClasspathKind kind);
    }

    private final RW.Constructor<LibraryWrapper> myLibraryWrapperConstructor =
            new RW.Constructor<LibraryWrapper>() {
                public LibraryWrapper read(final BufferedReader r) {
                    return new LibraryWrapper(r);
                }
            };

    public class LibraryWrapper implements ClasspathItemWrapper {
        final String myName;
        final List<String> myClassPath;

        public void write(final BufferedWriter w) {
            RW.writeln(w, "Library:" + myName);
            RW.writeln(w, "Classpath:");
            RW.writeln(w, myClassPath, RW.fromString);
        }

        public LibraryWrapper(final BufferedReader r) {
            myName = RW.readStringAttribute(r, "Library:");

            RW.readTag(r, "Classpath:");
            myClassPath = (List<String>) RW.readMany(r, RW.myStringConstructor, new ArrayList<String>());
        }

        public LibraryWrapper(final Library lib) {
            lib.forceInit();
            myName = lib.getName();
            myClassPath = (List<String>) getRelativePaths(lib.getClasspath(), new ArrayList<String>());
        }

        public String getName() {
            return myName;
        }

        public List<String> getClassPath(final ClasspathKind kind) {
            return myClassPath;
        }

        public int compareTo(Object o) {
            return getName().compareTo(((LibraryWrapper) o).getName());
        }
    }

    private final RW.Constructor<ClasspathItemWrapper> myWeakClasspathItemWrapperConstructor =
            new RW.Constructor<ClasspathItemWrapper>() {
                public ClasspathItemWrapper read(final BufferedReader r) {
                    final String s = RW.lookString(r);
                    if (s.startsWith("Library:")) {
                        return new WeakClasspathItemWrapper(RW.readStringAttribute(r, "Library:"), "Library");
                    }
                    if (s.startsWith("Module:")) {
                        return new WeakClasspathItemWrapper(RW.readStringAttribute(r, "Module:"), "Module");
                    } else {
                        return new GenericClasspathItemWrapper(r);
                    }
                }
            };

    public class WeakClasspathItemWrapper implements ClasspathItemWrapper {
        final String myName;
        final String myType;

        public WeakClasspathItemWrapper(final String name, final String type) {
            myName = name;
            myType = type;
        }

        public WeakClasspathItemWrapper(final ModuleWrapper m) {
            myType = "Module";
            myName = m.getName();
        }

        public WeakClasspathItemWrapper(final LibraryWrapper l) {
            myType = "Library";
            myName = l.getName();
        }

        public boolean isModule() {
            return myType.equals("Module");
        }

        public String getName() {
            return myName;
        }

        public int compareTo(Object o) {
            return getName().compareTo(((WeakClasspathItemWrapper) o).getName());
        }

        public List<String> getClassPath(ClasspathKind kind) {
            return null;
        }

        public void write(final BufferedWriter w) {
            RW.writeln(w, myType + ":" + getName());
        }
    }

    public class GenericClasspathItemWrapper implements ClasspathItemWrapper {
        final List<String> myClassPath;
        final String myType;

        public GenericClasspathItemWrapper(final ClasspathItem item) {
            if (item instanceof PathEntry)
                myType = "PathEntry";
            else if (item instanceof JavaSdk)
                myType = "JavaSdk";
            else if (item instanceof Sdk)
                myType = "Sdk";
            else
                myType = null;

            myClassPath = (List<String>) getRelativePaths(item.getClasspathRoots(null), new ArrayList<String>());
        }

        public GenericClasspathItemWrapper(final BufferedReader r) {
            myType = RW.readString(r);

            RW.readTag(r, "Classpath:");
            myClassPath = (List<String>) RW.readMany(r, RW.myStringConstructor, new ArrayList<String>());
        }

        public String getType() {
            return myType;
        }

        public List<String> getClassPath(final ClasspathKind kind) {
            return myClassPath;
        }

        public void write(final BufferedWriter w) {
            RW.writeln(w, myType);
            RW.writeln(w, "Classpath:");
            RW.writeln(w, myClassPath, RW.fromString);
        }

        public int compareTo(Object o) {
            final GenericClasspathItemWrapper w = (GenericClasspathItemWrapper) o;
            final int c = getType().compareTo(w.getType());
            return
                    c == 0 ?
                            (new Object() {
                                public int compare(Iterator<String> x, Iterator<String> y) {
                                    if (x.hasNext()) {
                                        if (y.hasNext()) {
                                            final int c = x.next().compareTo(y.next());

                                            return c == 0 ? compare(x, y) : c;
                                        }

                                        return 1;
                                    } else if (y.hasNext()) {
                                        return -1;
                                    }

                                    return 0;
                                }
                            }
                            ).compare(getClassPath(null).iterator(), w.getClassPath(null).iterator())
                            : c;
        }
    }

    private final RW.Constructor<FileWrapper> myFileWrapperConstructor =
            new RW.Constructor<FileWrapper>() {
                public FileWrapper read(final BufferedReader r) {
                    return new FileWrapper(r);
                }
            };

    public class FileWrapper implements RW.Writable {
        final String myName;
        final long myModificationTime;

        FileWrapper(final String name) {
            myName = name;
            myModificationTime = 0;
        }

        FileWrapper(final File f) {
            myName = getRelativePath(f.getAbsolutePath());
            myModificationTime = f.lastModified();
        }

        FileWrapper(final BufferedReader r) {
            myName = RW.readString(r);
            myModificationTime = 0; // readLong(r);

            final Set<ClassRepr> classes = (Set<ClassRepr>) RW.readMany(r, ClassRepr.constructor, new HashSet<ClassRepr> ());
            final Set<Usage> usages = (Set<Usage>) RW.readMany(r, Usage.constructor, new HashSet<Usage> ());

            myClassToSourceCallback.associate(classes, usages, myName);
        }

        public String getName() {
            return myName;
        }

        public long getStamp() {
            return myModificationTime;
        }

        public void write(final BufferedWriter w) {
            final String name = getName();

            RW.writeln(w, name);
            RW.writeln(w, mySourceToClass.getClasses(name));
            RW.writeln(w, mySourceToClass.getUsages(name));
            // writeln(w, Long.toString(getStamp()));
        }

        public int compareTo(Object o) {
            return getName().compareTo(((FileWrapper) o).getName());
        }

        public boolean equals(Object o) {
            return o instanceof FileWrapper && myName.equals(((FileWrapper) o).getName());
        }

        public int hashCode() {
            return myName.hashCode();
        }
    }

    private final RW.Constructor<ModuleWrapper> myModuleWrapperConstructor =
            new RW.Constructor<ModuleWrapper>() {
                public ModuleWrapper read(final BufferedReader r) {
                    return new ModuleWrapper(r);
                }
            };

    public class ModuleWrapper implements ClasspathItemWrapper {

        private class Properties implements RW.Writable {
            final Set<String> myRoots;
            final Set<FileWrapper> mySources;

            final String myOutput;
            String myOutputStatus;

            final long myLatestSource;
            final long myEarliestSource;

            long myLatestOutput;
            long myEarliestOutput;

            public void write(final BufferedWriter w) {
                RW.writeln(w, "Roots:");
                RW.writeln(w, myRoots, RW.fromString);

                RW.writeln(w, "Sources:");
                RW.writeln(w, mySources);

                RW.writeln(w, "Output:");
                RW.writeln(w, myOutput == null ? "" : myOutput);

                RW.writeln(w, "OutputStatus:" + myOutputStatus);

                //writeln(w, "EarliestSource:");
                //writeln(w, Long.toString(myEarliestSource));

                //writeln(w, "LatestSource:");
                //writeln(w, Long.toString(myLatestSource));

                //writeln(w, "EarliestOutput:");
                //writeln(w, Long.toString(myEarliestOutput));

                //writeln(w, "LatestOutput:");
                //writeln(w, Long.toString(myLatestOutput));
            }

            public Properties(final BufferedReader r) {
                RW.readTag(r, "Roots:");
                myRoots = (Set<String>) RW.readMany(r, RW.myStringConstructor, new HashSet<String>());

                RW.readTag(r, "Sources:");
                mySources = (Set<FileWrapper>) RW.readMany(r, myFileWrapperConstructor, new HashSet<FileWrapper>());

                RW.readTag(r, "Output:");
                final String s = RW.readString(r);
                myOutput = s.equals("") ? null : s;

                myOutputStatus = RW.readStringAttribute(r, "OutputStatus:");

                //readTag(r, "EarliestSource:");
                myEarliestSource = 0;//readLong(r);

                //readTag(r, "LatestSource:");
                myLatestSource = 0;//readLong(r);

                //readTag(r, "EarliestOutput:");
                myEarliestOutput = 0;//readLong(r);

                //readTag(r, "LatestOutput:");
                myLatestOutput = 0;//readLong(r);
            }

            public Properties(final List<String> sources, final String output, final Set<String> excludes) {
                myRoots = (Set<String>) getRelativePaths(sources, new HashSet<String>());

                {
                    final DirectoryScanner.Result result = DirectoryScanner.getFiles(myRoots, excludes, ProjectWrapper.this);
                    mySources = result.getFiles();
                    myEarliestSource = result.getEarliest();
                    myLatestSource = result.getLatest();
                }

                {
                    myOutput = getRelativePath(output);
                    rescan();
                }
            }

            public void rescan() {
                final DirectoryScanner.Result result = DirectoryScanner.getFiles(myOutput, null, ProjectWrapper.this);
                myOutputStatus =
                        result.getFiles().isEmpty()
                                ? "empty"
                                : (result.getFiles().contains(new FileWrapper(myOutput + File.separator + Reporter.myOkFlag))
                                ? "ok"
                                : "fail"
                        );
                myEarliestOutput = result.getEarliest();
                myLatestOutput = result.getLatest();
            }

            public Set<String> getRoots() {
                return myRoots;
            }

            public Set<FileWrapper> getSources() {
                return mySources;
            }

            public String getOutputPath() {
                return myOutput;
            }

            public long getEarliestOutput() {
                return myEarliestOutput;
            }

            public long getLatestOutput() {
                return myLatestOutput;
            }

            public long getEarliestSource() {
                return myEarliestSource;
            }

            public long getLatestSource() {
                return myLatestSource;
            }

            public boolean emptySource() {
                return mySources.isEmpty();
            }

            public boolean outputEmpty() {
                return myOutputStatus.equals("empty");
            }

            public boolean outputOk() {
                return myOutputStatus.equals("ok");
            }

            public boolean isOutdated() {
                return (!emptySource() && !outputOk()) || (getLatestSource() > getEarliestOutput());
            }

            public int compareTo(Object o) {
                return 0;
            }
        }

        final String myName;
        final Properties mySource;
        final Properties myTest;

        final Set<String> myExcludes;

        final Module myModule;
        List<ClasspathItemWrapper> myDependsOn;

        final Set<LibraryWrapper> myLibraries;

        public void rescan() {
            mySource.rescan();
            myTest.rescan();
        }

        private ClasspathItemWrapper weaken(final ClasspathItemWrapper x) {
            if (x instanceof ModuleWrapper) {
                return new WeakClasspathItemWrapper((ModuleWrapper) x);
            } else if (x instanceof LibraryWrapper) {
                return new WeakClasspathItemWrapper((LibraryWrapper) x);
            } else
                return x;
        }

        public void write(final BufferedWriter w) {
            RW.writeln(w, "Module:" + myName);

            RW.writeln(w, "SourceProperties:");
            mySource.write(w);

            RW.writeln(w, "TestProperties:");
            myTest.write(w);

            RW.writeln(w, "Excludes:");
            RW.writeln(w, myExcludes, RW.fromString);

            RW.writeln(w, "Libraries:");
            RW.writeln(w, myLibraries);

            RW.writeln(w, "Dependencies:");

            final List<ClasspathItemWrapper> weakened = new ArrayList<ClasspathItemWrapper>();

            for (ClasspathItemWrapper cpiw : dependsOn()) {
                weakened.add(weaken(cpiw));
            }

            RW.writeln(w, weakened);
        }

        public ModuleWrapper(final BufferedReader r) {
            myModule = null;
            myName = RW.readStringAttribute(r, "Module:");

            RW.readTag(r, "SourceProperties:");
            mySource = new Properties(r);

            RW.readTag(r, "TestProperties:");
            myTest = new Properties(r);

            RW.readTag(r, "Excludes:");
            myExcludes = (Set<String>) RW.readMany(r, RW.myStringConstructor, new HashSet<String>());

            RW.readTag(r, "Libraries:");
            myLibraries = (Set<LibraryWrapper>) RW.readMany(r, myLibraryWrapperConstructor, new HashSet<LibraryWrapper>());

            RW.readTag(r, "Dependencies:");
            myDependsOn = (List<ClasspathItemWrapper>) RW.readMany(r, myWeakClasspathItemWrapperConstructor, new ArrayList<ClasspathItemWrapper>());
        }

        public ModuleWrapper(final Module m) {
            m.forceInit();
            myModule = m;
            myDependsOn = null;
            myName = m.getName();
            myExcludes = (Set<String>) getRelativePaths(m.getExcludes(), new HashSet<String>());
            mySource = new Properties(m.getSourceRoots(), m.getOutputPath(), myExcludes);
            myTest = new Properties(m.getTestRoots(), m.getTestOutputPath(), myExcludes);

            myLibraries = new HashSet<LibraryWrapper>();

            for (Library lib : m.getLibraries().values()) {
                myLibraries.add(new LibraryWrapper(lib));
            }
        }

        public String getName() {
            return myName;
        }

        public Set<String> getSourceRoots() {
            return mySource.getRoots();
        }

        public Set<FileWrapper> getSourceFiles() {
            return mySource.getSources();
        }

        public String getOutputPath() {
            return mySource.getOutputPath();
        }

        public Set<String> getTestSourceRoots() {
            return myTest.getRoots();
        }

        public Set<FileWrapper> getTestSourceFiles() {
            return myTest.getSources();
        }

        public String getTestOutputPath() {
            return myTest.getOutputPath();
        }

        public List<ClasspathItemWrapper> dependsOn() {
            if (myDependsOn != null)
                return myDependsOn;

            myDependsOn = new ArrayList<ClasspathItemWrapper>();

            final ClasspathKind kind = myProject.getCompileClasspathKind(true);

            for (ClasspathItem cpi : myModule.getClasspath(kind)) {
                if (cpi instanceof Module) {
                    myDependsOn.add(getModule(((Module) cpi).getName()));
                } else if (cpi instanceof Library) {
                    myDependsOn.add(new LibraryWrapper((Library) cpi));
                } else {
                    myDependsOn.add(new GenericClasspathItemWrapper(cpi));
                }
            }

            return myDependsOn;
        }

        public List<String> getClassPath(final ClasspathKind kind) {
            final List<String> result = new ArrayList<String>();

            result.add(getOutputPath());

            if (kind.isTestsIncluded()) {
                result.add(getTestOutputPath());
            }

            return result;
        }

        private boolean safeEquals(final String a, final String b) {
            if (a == null || b == null)
                return a == b;

            return a.equals(b);
        }

        private boolean safeEquals(final ClasspathItemWrapper a, final ClasspathItemWrapper b) {
            try {
                final StringWriter as = new StringWriter();
                final StringWriter bs = new StringWriter();

                final BufferedWriter bas = new BufferedWriter(as);
                final BufferedWriter bbs = new BufferedWriter(bs);

                weaken(a).write(bas);
                weaken(b).write(bbs);

                bas.flush();
                bbs.flush();

                as.close();
                bs.close();

                final String x = as.getBuffer().toString();
                final String y = bs.getBuffer().toString();

                return x.equals(y);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        public boolean isOutdated(final boolean tests, final ProjectWrapper history) {
            if (history == null)
                return true;

            final ModuleWrapper past = history.getModule(myName);
            final boolean isNewModule = past == null;
            final boolean outputChanged = !isNewModule && !safeEquals(past.getOutputPath(), getOutputPath());
            final boolean testOutputChanged = !isNewModule && tests && !safeEquals(past.getTestOutputPath(), getTestOutputPath());
            final boolean sourceChanged = !isNewModule && !past.getSourceFiles().equals(getSourceFiles());
            final boolean testSourceChanged = !isNewModule && tests && !past.getTestSourceFiles().equals(getTestSourceFiles());
            final boolean sourceOutdated = mySource.isOutdated();
            final boolean testSourceOutdated = tests && myTest.isOutdated();
            final boolean unsafeDependencyChange = !isNewModule && (
                    new Object() {
                        public boolean run(final List<ClasspathItemWrapper> today, final List<ClasspathItemWrapper> yesterday) {
                            final Iterator<ClasspathItemWrapper> t = today.iterator();
                            final Iterator<ClasspathItemWrapper> y = yesterday.iterator();

                            while (true) {
                                if (!y.hasNext())
                                    return false;

                                if (!t.hasNext())
                                    return true;

                                if (!safeEquals(t.next(), y.next()))
                                    return true;
                            }
                        }
                    }.run(dependsOn(), past.dependsOn())
            );

            return sourceOutdated ||
                    testSourceOutdated ||
                    sourceChanged ||
                    testSourceChanged ||
                    outputChanged ||
                    testOutputChanged ||
                    unsafeDependencyChange ||
                    isNewModule;
        }

        public int compareTo(Object o) {
            return getName().compareTo(((ModuleWrapper) o).getName());
        }
    }

    final Map<String, ModuleWrapper> myModules = new HashMap<String, ModuleWrapper>();
    final Map<String, LibraryWrapper> myLibraries = new HashMap<String, LibraryWrapper>();
    final ProjectWrapper myHistory;
    final SourceToClass mySourceToClass = new SourceToClass(this);
    final Callbacks.Backend myClassToSourceCallback = mySourceToClass.getCallback();

    private void rescan() {
        for (ModuleWrapper m : myModules.values()) {
            m.rescan();
        }
    }

    public ModuleWrapper getModule(final String name) {
        return myModules.get(name);
    }

    public LibraryWrapper getLibrary(final String name) {
        return myLibraries.get(name);
    }

    public Collection<LibraryWrapper> getLibraries() {
        return myLibraries.values();
    }

    public Collection<ModuleWrapper> getModules() {
        return myModules.values();
    }

    private ProjectWrapper(final String prjDir, final String setupScript) {
        myProject = new Project(new GantBinding(), mySourceToClass.getCallback());
        myRoot = new File(prjDir).getAbsolutePath();
        myProjectSnapshot = myHomeDir + File.separator + myJPSDir + File.separator + myRoot.replace(File.separatorChar, myFileSeparatorReplacement);

        IdeaProjectLoader.loadFromPath(myProject, getAbsolutePath(myIDEADir), setupScript);

        for (Module m : myProject.getModules().values()) {
            myModules.put(m.getName(), new ModuleWrapper(m));
        }

        for (Library l : myProject.getLibraries().values()) {
            myLibraries.put(l.getName(), new LibraryWrapper(l));
        }

        myHistory = loadSnapshot();

        if (myHistory != null)
            mySourceToClass.inherit(myHistory.mySourceToClass);
    }

    public String getAbsolutePath(final String relative) {
        if (relative == null)
            return relative;

        if (new File(relative).isAbsolute())
            return relative;

        return myRoot + File.separator + relative;
    }

    public String getRelativePath(final String absolute) {
        if (absolute == null)
            return absolute;

        if (absolute.startsWith(myRoot)) {
            return absolute.substring(myRoot.length() + 1);
        }

        return absolute;
    }

    public Collection<String> getAbsolutePaths(final Collection<String> paths, final Collection<String> result) {
        for (String path : paths) {
            if (path != null)
                result.add(getAbsolutePath(path));
        }

        return result;
    }

    public Collection<String> getRelativePaths(final Collection<String> paths, final Collection<String> result) {
        for (String path : paths) {
            if (path != null)
                result.add(getRelativePath(path));
        }

        return result;
    }

    private boolean isHistory() {
        return myProject == null;
    }

    private ProjectWrapper(final BufferedReader r) {
        myProject = null;
        myHistory = null;

        myRoot = RW.readStringAttribute(r, "Root:");
        myProjectSnapshot = myHomeDir + File.separator + myJPSDir + File.separator + myRoot.replace(File.separatorChar, myFileSeparatorReplacement);

        RW.readTag(r, "Libraries:");
        final Set<LibraryWrapper> libs = (Set<LibraryWrapper>) RW.readMany(r, myLibraryWrapperConstructor, new HashSet<LibraryWrapper>());

        for (LibraryWrapper l : libs) {
            myLibraries.put(l.getName(), l);
        }

        RW.readTag(r, "Modules:");
        final Set<ModuleWrapper> mods = (Set<ModuleWrapper>) RW.readMany(r, myModuleWrapperConstructor, new HashSet<ModuleWrapper>());

        for (ModuleWrapper m : mods) {
            myModules.put(m.getName(), m);
        }
    }

    public void write(final BufferedWriter w) {
        RW.writeln(w, "Root:" + myRoot);

        RW.writeln(w, "Libraries:");
        RW.writeln(w, getLibraries());

        RW.writeln(w, "Modules:");
        RW.writeln(w, getModules());
    }

    private String getProjectSnapshotFileName() {
        return myProjectSnapshot;
    }

    private ProjectWrapper loadSnapshot() {
        initJPSDirectory();

        try {
            final BufferedReader r = new BufferedReader(new FileReader(getProjectSnapshotFileName()));
            final ProjectWrapper w = new ProjectWrapper(r);
            r.close();

            return w;
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void saveSnapshot() {
        initJPSDirectory();

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(getProjectSnapshotFileName()));

            write(bw);

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ProjectWrapper load(final String path, final String setupScript) {
        return new ProjectWrapper(path, setupScript);
    }

    public void report(final String module) {
        final ModuleWrapper m = getModule(module);

        if (m == null) {
            System.out.println("No module \"" + module + "\" found in project \"");
        } else {
            System.out.println("Module " + m.myName + " " + (m.isOutdated(false, myHistory) ? "is outdated" : "is up-to-date"));
            System.out.println("Module " + m.myName + " tests " + (m.isOutdated(true, myHistory) ? "are outdated" : "are up-to-date"));
        }
    }

    public void report() {
        boolean moduleReport = true;

        System.out.println("Project \"" + myRoot + "\" report:");

        if (myHistory == null) {
            System.out.println("   no project history found");
        }

        if (moduleReport) {
            for (ModuleWrapper m : myModules.values()) {
                System.out.println("   module " + m.getName() + " " + (m.isOutdated(false, myHistory) ? "is outdated" : "is up-to-date"));
                System.out.println("   module " + m.getName() + " tests " + (m.isOutdated(true, myHistory) ? "are outdated" : "are up-to-date"));
            }
        }
    }

    public void save() {
        saveSnapshot();
    }

    public void clean() {
        myProject.clean();
        rescan();
    }

    public void rebuild() {
        myProject.makeAll();
        rescan();
    }

    public void make(final boolean force, final boolean tests) {
        final List<Module> modules = new ArrayList<Module>();

        for (Map.Entry<String, ModuleWrapper> entry : myModules.entrySet()) {
            if (force || entry.getValue().isOutdated(tests, myHistory))
                modules.add(myProject.getModules().get(entry.getKey()));
        }

        if (modules.isEmpty() && !force) {
            System.out.println("All modules are up-to-date.");
            return;
        }

        System.out.println("Rebuilding modules:");

        for (Module m : modules)
            System.out.println("  " + m.getName());

        makeModules(modules, tests, force);
    }

    private void makeModules(final Collection<Module> initial, final boolean tests, final boolean force) {
        final ClasspathKind kind = myProject.getCompileClasspathKind(tests);

        final Set<Module> modules = new HashSet<Module>();
        final Set<Module> marked = new HashSet<Module>();
        final Map<Module, Boolean> visited = new HashMap<Module, Boolean>();
        final Set<Module> frontier = new HashSet<Module>();

        final Map<Module, Set<Module>> reversedDependencies = new HashMap<Module, Set<Module>>();

        DotPrinter.header();

        for (Module m : myProject.getModules().values()) {

            DotPrinter.node(m.getName());

            for (ClasspathItem cpi : m.getClasspath(kind)) {
                if (cpi instanceof Module) {
                    DotPrinter.edge(((Module) cpi).getName(), m.getName());

                    Set<Module> sm = reversedDependencies.get(cpi);

                    if (sm == null) {
                        sm = new HashSet<Module>();
                        reversedDependencies.put((Module) cpi, sm);
                    }

                    sm.add(m);
                }
            }
        }

        DotPrinter.footer();

        // Building "upper" subgraph

        DotPrinter.header();

        new Object() {
            public void run(final Collection<Module> initial) {
                if (initial == null)
                    return;

                for (Module module : initial) {
                    if (marked.contains(module))
                        continue;

                    DotPrinter.node(module.getName());

                    final List<Module> dep = new ArrayList<Module>();

                    for (ClasspathItem cpi : module.getClasspath(kind)) {
                        if (cpi instanceof Module) {
                            DotPrinter.edge(((Module) cpi).getName(), module.getName());
                            dep.add((Module) cpi);
                        }
                    }

                    if (dep.size() == 0) {
                        frontier.add(module);
                    }

                    marked.add(module);

                    run(dep);
                }
            }
        }.run(initial);

        DotPrinter.footer();

        // Traversing "upper" subgraph and collecting outdated modules and their descendants
        new Object() {
            public void run(final Collection<Module> initial, final boolean force) {
                if (initial == null)
                    return;

                for (Module module : initial) {
                    if (!marked.contains(module))
                        continue;

                    final Boolean property = visited.get(module);

                    if (property == null || !property && force) {
                        if (force || getModule(module.getName()).isOutdated(tests, myHistory)) {
                            visited.put(module, true);
                            modules.add(module);
                            run(reversedDependencies.get(module), true);
                        } else {
                            if (property == null) {
                                visited.put(module, false);
                            }
                            run(reversedDependencies.get(module), false);
                        }
                    }
                }
            }
        }.run(frontier, force);

        if (modules.size() == 0 && !force) {
            System.out.println("All requested modules are up-to-date.");
            return;
        }

        myProject.makeSelected(modules, tests);
        rescan();
    }

    public void makeModule(final String modName, final boolean force, final boolean tests) {
        final Module module = myProject.getModules().get(modName);
        final List<Module> list = new ArrayList<Module>();

        if (module == null) {
            System.err.println("Module \"" + modName + "\" not found in project \"" + myRoot + "\"");
            return;
        }

        list.add(module);

        makeModules(list, tests, force);
    }
}
