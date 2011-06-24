package org.jetbrains.ether;

import org.codehaus.gant.GantBinding;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.ether.dependencyView.*;
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
    public interface Flags {
        boolean tests();

        boolean incremental();

        boolean force();
    }

    private final static Flags defaultFlags = new Flags() {
        public boolean tests() {
            return true;
        }

        public boolean incremental() {
            return false;
        }

        public boolean force() {
            return true;
        }
    };

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

    private final RW.Reader<LibraryWrapper> myLibraryWrapperReader =
            new RW.Reader<LibraryWrapper>() {
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
            myClassPath = (List<String>) RW.readMany(r, RW.myStringReader, new ArrayList<String>());
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LibraryWrapper that = (LibraryWrapper) o;

            if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return myName != null ? myName.hashCode() : 0;
        }
    }

    private final RW.Reader<ClasspathItemWrapper> myWeakClasspathItemWrapperReader =
            new RW.Reader<ClasspathItemWrapper>() {
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
            myClassPath = (List<String>) RW.readMany(r, RW.myStringReader, new ArrayList<String>());
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
    }

    private final RW.Reader<FileWrapper> myFileWrapperReader =
            new RW.Reader<FileWrapper>() {
                public FileWrapper read(final BufferedReader r) {
                    return new FileWrapper(r);
                }
            };

    public class FileWrapper implements RW.Writable {
        final StringCache.S myName;
        final long myModificationTime;

        FileWrapper(final File f) {
            myName = StringCache.get(getRelativePath(f.getAbsolutePath()));
            myModificationTime = f.lastModified();
        }

        FileWrapper(final BufferedReader r) {
            myName = StringCache.get(RW.readString(r));
            myModificationTime = RW.readLong(r);

            final Set<ClassRepr> classes = (Set<ClassRepr>) RW.readMany(r, ClassRepr.reader, new HashSet<ClassRepr>());
            final Set<UsageRepr.Usage> usages = (Set<UsageRepr.Usage>) RW.readMany(r, UsageRepr.reader, new HashSet<UsageRepr.Usage>());
            final Set<StringCache.S> formClasses = (Set<StringCache.S>) RW.readMany(r, StringCache.S.reader, new HashSet<StringCache.S>());

            backendCallback.associate(classes, usages, myName.value);

            for (StringCache.S classFileName : formClasses) {
                backendCallback.associateForm(myName, classFileName);
            }
        }

        public StringCache.S getName() {
            return myName;
        }

        public long getStamp() {
            return myModificationTime;
        }

        public void write(final BufferedWriter w) {
            final StringCache.S name = getName();

            RW.writeln(w, name.value);
            RW.writeln(w, Long.toString(getStamp()));

            RW.writeln(w, dependencyMapping.getClasses(name));
            RW.writeln(w, dependencyMapping.getUsages(name));
            RW.writeln(w, dependencyMapping.getFormClass(name));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FileWrapper that = (FileWrapper) o;

            if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return myName != null ? myName.hashCode() : 0;
        }
    }

    private final RW.Reader<ModuleWrapper> myModuleWrapperReader =
            new RW.Reader<ModuleWrapper>() {
                public ModuleWrapper read(final BufferedReader r) {
                    return new ModuleWrapper(r);
                }
            };

    public class ModuleWrapper implements ClasspathItemWrapper {

        private class Properties implements RW.Writable {
            final Set<String> myRoots;
            final Map<FileWrapper, FileWrapper> mySources;

            final String myOutput;
            String myOutputStatus;

            public Set<StringCache.S> getFiles() {
                final Set<StringCache.S> result = new HashSet<StringCache.S>();

                for (FileWrapper f : mySources.keySet()) {
                    result.add(f.getName());
                }

                return result;
            }

            public Set<StringCache.S> getOutdatedFiles(final Properties past) {
                final Set<StringCache.S> result = new HashSet<StringCache.S>();

                for (FileWrapper now : mySources.keySet()) {
                    final FileWrapper than = past == null ? null : past.mySources.get(now);

                    if (than == null || than.getStamp() < now.getStamp() || affectedFiles.contains(now.getName())) {
                        result.add(now.getName());
                    }
                }

                return result;
            }

            public Set<StringCache.S> getRemovedFiles(final Properties past) {
                final Set<StringCache.S> result = new HashSet<StringCache.S>();

                if (past != null) {
                    for (FileWrapper was : past.mySources.keySet()) {
                        final FileWrapper now = mySources.get(was);

                        if (now == null) {
                            result.add(was.getName());
                        }
                    }
                }

                return result;
            }

            public void write(final BufferedWriter w) {
                RW.writeln(w, "Roots:");
                RW.writeln(w, myRoots, RW.fromString);

                RW.writeln(w, "Sources:");
                RW.writeln(w, mySources.keySet());

                RW.writeln(w, "Output:");
                RW.writeln(w, myOutput == null ? "" : myOutput);

                RW.writeln(w, "OutputStatus:" + myOutputStatus);
            }

            public Properties(final BufferedReader r) {
                RW.readTag(r, "Roots:");

                myRoots = (Set<String>) RW.readMany(r, RW.myStringReader, new HashSet<String>());

                RW.readTag(r, "Sources:");

                mySources = new HashMap<FileWrapper, FileWrapper>();

                for (FileWrapper fw : (Set<FileWrapper>) RW.readMany(r, myFileWrapperReader, new HashSet<FileWrapper>())) {
                    mySources.put(fw, fw);
                }

                RW.readTag(r, "Output:");
                final String s = RW.readString(r);
                myOutput = s.equals("") ? null : s;

                myOutputStatus = RW.readStringAttribute(r, "OutputStatus:");
            }

            public Properties(final List<String> sources, final String output, final Set<String> excludes) {
                myRoots = (Set<String>) getRelativePaths(sources, new HashSet<String>());
                final DirectoryScanner.Result result = DirectoryScanner.getFiles(myRoots, excludes, ProjectWrapper.this);

                mySources = new HashMap<FileWrapper, FileWrapper>();

                for (FileWrapper fw : result.getFiles()) {
                    mySources.put(fw, fw);
                }

                myOutput = getRelativePath(output);
                updateOutputStatus();
            }

            public void updateOutputStatus() {
                final String path = getAbsolutePath(myOutput);
                final File ok = new File(path + File.separator + Reporter.myOkFlag);
                final File fail = new File(path + File.separator + Reporter.myFailFlag);

                if (ok.exists()) {
                    myOutputStatus = "ok";
                    return;
                }

                if (fail.exists()) {
                    myOutputStatus = "fail";
                    return;
                }

                myOutputStatus = "empty";
            }

            public Set<String> getRoots() {
                return myRoots;
            }

            public Set<FileWrapper> getSources() {
                return mySources.keySet();
            }

            public String getOutputPath() {
                return myOutput;
            }

            public boolean emptySource() {
                return mySources.isEmpty();
            }

            public boolean outputOk() {
                return myOutputStatus.equals("ok");
            }

            public boolean isOutdated() {
                return (!emptySource() && !outputOk());
            }
        }

        final String myName;
        final Properties mySource;
        final Properties myTest;

        final Set<String> myExcludes;

        final Module myModule;
        List<ClasspathItemWrapper> myDependsOn;

        final Set<LibraryWrapper> myLibraries;

        public Set<StringCache.S> getOutdatedSources() {
            return mySource.getOutdatedFiles(myHistory == null ? null : myHistory.getModule(myName).mySource);
        }

        public Set<StringCache.S> getOutdatedTests() {
            return myTest.getOutdatedFiles(myHistory == null ? null : myHistory.getModule(myName).myTest);
        }

        public Set<StringCache.S> getRemovedSources() {
            return mySource.getRemovedFiles(myHistory == null ? null : myHistory.getModule(myName).mySource);
        }

        public Set<StringCache.S> getRemovedTests() {
            return myTest.getRemovedFiles(myHistory == null ? null : myHistory.getModule(myName).myTest);
        }

        public void updateOutputStatus() {
            mySource.updateOutputStatus();
            myTest.updateOutputStatus();
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
            myExcludes = (Set<String>) RW.readMany(r, RW.myStringReader, new HashSet<String>());

            RW.readTag(r, "Libraries:");
            myLibraries = (Set<LibraryWrapper>) RW.readMany(r, myLibraryWrapperReader, new HashSet<LibraryWrapper>());

            RW.readTag(r, "Dependencies:");
            myDependsOn = (List<ClasspathItemWrapper>) RW.readMany(r, myWeakClasspathItemWrapperReader, new ArrayList<ClasspathItemWrapper>());
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

        public Set<FileWrapper> getTestFiles() {
            return myTest.getSources();
        }

        public Set<StringCache.S> getSources() {
            return mySource.getFiles();
        }

        public Set<StringCache.S> getTests() {
            return myTest.getFiles();
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
            if (past == null)
                return true;

            final boolean outputChanged = !safeEquals(past.getOutputPath(), getOutputPath());
            final boolean testOutputChanged = tests && !safeEquals(past.getTestOutputPath(), getTestOutputPath());
            final boolean sourceChanged = !past.getSourceFiles().equals(getSourceFiles());
            final boolean testSourceChanged = tests && !past.getTestSourceFiles().equals(getTestSourceFiles());
            final boolean sourceOutdated = mySource.isOutdated() || !mySource.getOutdatedFiles(past.mySource).isEmpty();
            final boolean testSourceOutdated = tests && (myTest.isOutdated() || !myTest.getOutdatedFiles(past.myTest).isEmpty());
            final boolean unsafeDependencyChange = (
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
                    unsafeDependencyChange;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ModuleWrapper that = (ModuleWrapper) o;

            if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return myName != null ? myName.hashCode() : 0;
        }
    }

    final Map<String, ModuleWrapper> myModules = new HashMap<String, ModuleWrapper>();
    final Map<String, LibraryWrapper> myLibraries = new HashMap<String, LibraryWrapper>();

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

    final Mappings dependencyMapping;
    final Callbacks.Backend backendCallback;
    final Set<StringCache.S> affectedFiles;

    final ProjectWrapper myHistory;

    private static String getCanonicalPath(final String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ProjectWrapper(final GantBinding binding, final String prjDir, final String setupScript, final Map<String, String> pathVariables) {
        dependencyMapping = new Mappings(this);
        backendCallback = dependencyMapping.getCallback();
        affectedFiles = new HashSet<StringCache.S>();

        myProject = new Project(binding == null ? new GantBinding() : binding);

        final File prjFile = new File(prjDir);
        final boolean dirBased = !(prjFile.isFile() && prjDir.endsWith(".ipr"));

        myRoot = dirBased ? getCanonicalPath(prjDir) : getCanonicalPath(prjFile.getPath());

        final String loadPath = dirBased ? getAbsolutePath(myIDEADir) : prjDir;

        myProjectSnapshot = myHomeDir + File.separator + myJPSDir + File.separator + myRoot.replace(File.separatorChar, myFileSeparatorReplacement);

        if (pathVariables == null) {
            IdeaProjectLoader.loadFromPath(myProject, loadPath, setupScript);
        } else {
            IdeaProjectLoader.loadFromPath(myProject, loadPath, pathVariables, setupScript);
        }

        for (Module m : myProject.getModules().values()) {
            myModules.put(m.getName(), new ModuleWrapper(m));
        }

        for (Library l : myProject.getLibraries().values()) {
            myLibraries.put(l.getName(), new LibraryWrapper(l));
        }

        myHistory = loadSnapshot(dependencyMapping, affectedFiles);
    }

    private ProjectWrapper(final BufferedReader r, final Mappings mappings, final Set<StringCache.S> affected) {
        dependencyMapping = mappings;
        affectedFiles = affected;
        backendCallback = dependencyMapping.getCallback();
        myProject = null;
        myHistory = null;

        myRoot = RW.readStringAttribute(r, "Root:");
        myProjectSnapshot = myHomeDir + File.separator + myJPSDir + File.separator + myRoot.replace(File.separatorChar, myFileSeparatorReplacement);

        RW.readTag(r, "Libraries:");
        final Set<LibraryWrapper> libs = (Set<LibraryWrapper>) RW.readMany(r, myLibraryWrapperReader, new HashSet<LibraryWrapper>());

        for (LibraryWrapper l : libs) {
            myLibraries.put(l.getName(), l);
        }

        RW.readTag(r, "Modules:");
        final Set<ModuleWrapper> mods = (Set<ModuleWrapper>) RW.readMany(r, myModuleWrapperReader, new HashSet<ModuleWrapper>());

        for (ModuleWrapper m : mods) {
            myModules.put(m.getName(), m);
        }

        RW.readMany(r, StringCache.reader, affectedFiles);
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

    public void write(final BufferedWriter w) {
        RW.writeln(w, "Root:" + myRoot);

        RW.writeln(w, "Libraries:");
        RW.writeln(w, getLibraries());

        RW.writeln(w, "Modules:");
        RW.writeln(w, getModules());

        RW.writeln(w, affectedFiles, StringCache.fromS);
    }

    private String getProjectSnapshotFileName() {
        return myProjectSnapshot;
    }

    private ProjectWrapper loadSnapshot(final Mappings mappings, final Set<StringCache.S> affectedFiles) {
        initJPSDirectory();

        try {
            final BufferedReader r = new BufferedReader(new FileReader(getProjectSnapshotFileName()));
            final ProjectWrapper w = new ProjectWrapper(r, mappings, affectedFiles);
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
        return new ProjectWrapper(null, path, setupScript, null);
    }

    public static ProjectWrapper load(final GantBinding binding, final String path, final String setupScript, final Map<String, String> pathVariables) {
        return new ProjectWrapper(binding, path, setupScript, pathVariables);
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
        for (ModuleWrapper m : myModules.values())
            m.updateOutputStatus();
    }

    public void rebuild() {
        makeModules(myProject.getModules().values(), defaultFlags);
    }

    public void buildArtifacts() {
        myProject.buildArtifacts();
    }

    public void deleteTempFiles() {
        myProject.deleteTempFiles();
    }

    public Project getProject() {
        return myProject;
    }

    class BusyBeaver {
        final ProjectBuilder builder;
        final Set<StringCache.S> compiledFiles = new HashSet<StringCache.S>();
        final Set<Module> cleared = new HashSet<Module>();

        //Mappings delta = new Mappings(ProjectWrapper.this);

        BusyBeaver(ProjectBuilder builder) {
            this.builder = builder;
        }

        boolean iterativeCompile(final ModuleChunk chunk, final boolean tests, final Set<StringCache.S> sources, final Set<StringCache.S> outdated, final Set<StringCache.S> removed) {
            final Collection<StringCache.S> filesToCompile = DefaultGroovyMethods.intersect(affectedFiles, sources);
            final Set<StringCache.S> safeFiles = new HashSet<StringCache.S>();

            if (outdated != null) {
                for (StringCache.S s : outdated) {
                    assert (s != null);
                }

                filesToCompile.addAll(outdated);

                for (StringCache.S f : outdated) {
                    if (f.value.endsWith(".form")) {
                        final StringCache.S sourceFileName = dependencyMapping.getJavaByForm(f);

                        if (sourceFileName != null && !filesToCompile.contains(sourceFileName)) {
                            safeFiles.add(sourceFileName);
                            filesToCompile.add(sourceFileName);
                        }
                    } else if (f.value.endsWith(".java")) {
                        final StringCache.S formFileName = dependencyMapping.getFormByJava(f);

                        if (formFileName != null) {
                            filesToCompile.add(formFileName);
                        }
                    }
                }
            }

            filesToCompile.removeAll(compiledFiles);

            if (!filesToCompile.isEmpty() || removed != null) {
                final Set<StringCache.S> outputFiles = new HashSet<StringCache.S>();

                for (StringCache.S f : filesToCompile) {
                    final Set<ClassRepr> classes = dependencyMapping.getClasses(f);

                    if (classes != null)
                        for (ClassRepr cr : classes) {
                            outputFiles.add(cr.fileName);
                        }
                }

                if (removed != null) {
                    for (StringCache.S f : removed) {
                        final Set<ClassRepr> classes = dependencyMapping.getClasses(f);
                        if (classes != null) {
                            for (ClassRepr cr : classes) {
                                outputFiles.add(cr.fileName);
                            }
                        }
                    }
                }

                if (!outputFiles.isEmpty()) {
                    builder.clearChunk(chunk, outputFiles, ProjectWrapper.this);
                }

                final Mappings delta = new Mappings(ProjectWrapper.this);
                final Callbacks.Backend deltaBackend = delta.getCallback();

                builder.buildChunk(chunk, tests, filesToCompile, deltaBackend, ProjectWrapper.this);

                compiledFiles.addAll(filesToCompile);
                affectedFiles.removeAll(filesToCompile);

                final boolean incremental = dependencyMapping.differentiate(delta, removed, compiledFiles, affectedFiles, safeFiles);

                dependencyMapping.integrate(delta, removed);

                if (!incremental) {
                    affectedFiles.addAll(sources);
                    iterativeCompile(chunk, tests, sources, null, null);
                    return false;
                }

                return iterativeCompile(chunk, tests, sources, null, null);
            } else {
                for (Module m : chunk.getElements()) {
                    Reporter.reportBuildSuccess(m, tests);
                }
            }

            return true;
        }

        public void build(final Collection<Module> modules, final boolean tests, boolean incremental) {
            final List<ModuleChunk> chunks = myProject.getChunks(tests);

            for (ModuleChunk c : chunks) {
                final Set<Module> chunkModules = c.getElements();

                if (!DefaultGroovyMethods.intersect(modules, chunkModules).isEmpty()) {
                    if (incremental) {
                        final Set<StringCache.S> chunkSources = new HashSet<StringCache.S>();
                        final Set<StringCache.S> outdatedSources = new HashSet<StringCache.S>();
                        final Set<StringCache.S> removedSources = new HashSet<StringCache.S>();

                        for (Module m : chunkModules) {
                            final ModuleWrapper mw = getModule(m.getName());
                            outdatedSources.addAll(tests ? mw.getOutdatedTests() : mw.getOutdatedSources());
                            chunkSources.addAll(tests ? mw.getTests() : mw.getSources());
                            removedSources.addAll(tests ? mw.getRemovedTests() : mw.getRemovedSources());
                        }

                        incremental = iterativeCompile(c, tests, chunkSources, outdatedSources, removedSources);
                    } else {
                        final Set<Module> toClean = new HashSet<Module>();

                        for (Module m : chunkModules) {
                            if (!cleared.contains(m)) {
                                toClean.add(m);
                            }
                        }

                        if (!toClean.isEmpty()) {
                            builder.clearChunk(new ModuleChunk(toClean), null, ProjectWrapper.this);
                            cleared.addAll(toClean);
                        }

                        builder.buildChunk(c, tests, null, backendCallback, ProjectWrapper.this);

                        for (Module m : c.getElements()) {
                            final ModuleWrapper module = getModule(m.getName());
                            affectedFiles.removeAll(tests ? module.getTests() : module.getSources());
                        }
                    }
                }
            }
        }
    }

    private void makeModules(final Collection<Module> initial, final Flags flags) {
        final ClasspathKind kind = myProject.getCompileClasspathKind(flags.tests());

        final Set<Module> modules = new HashSet<Module>();
        final Set<String> marked = new HashSet<String>();
        final Map<String, Boolean> visited = new HashMap<String, Boolean>();
        final Set<String> frontier = new HashSet<String>();

        final Map<String, Set<String>> reversedDependencies = new HashMap<String, Set<String>>();

        DotPrinter.header();

        for (Module m : myProject.getModules().values()) {
            final String mName = m.getName();

            DotPrinter.node(mName);

            for (ClasspathItem cpi : m.getClasspath(kind)) {
                if (cpi instanceof Module) {
                    final String name = ((Module) cpi).getName();

                    DotPrinter.edge(name, mName);

                    Set<String> sm = reversedDependencies.get(name);

                    if (sm == null) {
                        sm = new HashSet<String>();
                        reversedDependencies.put(name, sm);
                    }

                    sm.add(mName);
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

                    final String mName = module.getName();

                    if (marked.contains(mName))
                        continue;

                    DotPrinter.node(mName);

                    final List<Module> dep = new ArrayList<Module>();

                    for (ClasspathItem cpi : module.getClasspath(kind)) {
                        if (cpi instanceof Module && !marked.contains(((Module) cpi).getName())) {
                            DotPrinter.edge(((Module) cpi).getName(), mName);
                            dep.add((Module) cpi);
                        }
                    }

                    if (dep.size() == 0) {
                        frontier.add(mName);
                    }

                    marked.add(mName);

                    run(dep);
                }
            }
        }.run(initial);

        DotPrinter.footer();

        // Traversing "upper" subgraph and collecting outdated modules and their descendants
        new Object() {
            public void run(final Collection<String> initial, final boolean force) {
                if (initial == null)
                    return;

                for (String moduleName : initial) {
                    if (!marked.contains(moduleName))
                        continue;

                    final Boolean property = visited.get(moduleName);

                    if (property == null || !property && force) {
                        if (force || getModule(moduleName).isOutdated(flags.tests(), myHistory)) {
                            visited.put(moduleName, true);
                            modules.add(myProject.getModules().get(moduleName));
                            run(reversedDependencies.get(moduleName), true);
                        } else {
                            if (property == null) {
                                visited.put(moduleName, false);
                            }
                            run(reversedDependencies.get(moduleName), false);
                        }
                    }
                }
            }
        }.run(frontier, flags.force());

        if (modules.size() == 0 && !flags.force()) {
            System.out.println("All requested modules are up-to-date.");
            return;
        }

        final ProjectBuilder builder = myProject.getBuilder();
        final BusyBeaver beaver = new BusyBeaver(builder);

        builder.buildStart();

        beaver.build(modules, false, flags.incremental());

        if (flags.tests()) {
            beaver.build(modules, true, flags.incremental());
        }

        builder.buildStop();

        for (Module mod : modules) {
            getModule(mod.getName()).updateOutputStatus();
        }
    }

    public void makeModule(final String modName, final Flags flags) {
        if (modName == null) {
            makeModules(myProject.getModules().values(), flags);
        } else {
            final Module module = myProject.getModules().get(modName);
            final List<Module> list = new ArrayList<Module>();

            if (module == null) {
                System.err.println("Module \"" + modName + "\" not found in project \"" + myRoot + "\"");
                return;
            }

            list.add(module);

            makeModules(list, flags);
        }
    }
}
