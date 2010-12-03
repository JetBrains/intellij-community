package org.jetbrains.ether;

import com.sun.org.apache.xpath.internal.operations.Mod;
import org.codehaus.gant.GantBinding;
import org.jetbrains.jps.ClasspathItem;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.idea.IdeaProjectLoader;

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

    // JPS directory initialization
    private static void initJPSDirectory () {
        final File f = new File(myHomeDir + File.separator + myJPSDir);

        if (! f.exists())
            f.mkdir();
    }

    // File separator replacement
    private static final char myFileSeparatorReplacement = '-';

    // Original JPS Project
    private final Project myProject;

    // Project directory
    private final String myRoot;

    // Project snapshot file
    private final String myProjectSnapshot;

    // Project history
    private ProjectSnapshot mySnapshot;
    private ProjectSnapshot myPresent;

    public ProjectWrapper(final String prjDir) {
        myProject = new Project(new GantBinding());
        myRoot = new File (prjDir).getAbsolutePath();
        myProjectSnapshot = myHomeDir + File.separator + myJPSDir + File.separator + myRoot.replace(File.separatorChar, myFileSeparatorReplacement);
    }

    private String getProjectSnapshotFileName() {
        return myProjectSnapshot;
    }

    private ProjectSnapshot loadSnapshot() {
        initJPSDirectory();

        ProjectSnapshot result = null;

        try {
            final String path = getProjectSnapshotFileName();

            byte[] buffer = new byte[(int) new File(path).length()];

            BufferedInputStream f = new BufferedInputStream(new FileInputStream(path));

            f.read(buffer);

            f.close();

            result = new ProjectSnapshot(new String(buffer));
        }
        catch (FileNotFoundException e) {

        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private void saveSnapshot() {
        initJPSDirectory();

        final ProjectSnapshot snapshot = StatusCollector.collectHistory(myProject);

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(getProjectSnapshotFileName()));

            bw.write(snapshot.toString());

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        IdeaProjectLoader.loadFromPath(myProject, myRoot);
        mySnapshot = loadSnapshot();
        myPresent = StatusCollector.collectHistory(myProject);
    }

    public void report(final String module) {
        final ModuleStatus m = myPresent.myModuleHistories.get(module);

        if (m == null) {
            System.out.println("No module \"" + module + "\" found in project \"");
        } else {
            System.out.println("Module " + m.myName + " " + (m.isOutdated(false) ? "is outdated" : "is up-to-date"));
            System.out.println("Module " + m.myName + " tests " + (m.isOutdated(true) ? "are outdated" : "are up-to-date"));
        }
    }

    private boolean structureChanged() {
        if (mySnapshot == null)
            return true;

        return myPresent.structureChanged(mySnapshot);
    }

    public void report() {
        boolean moduleReport = true;

        System.out.println("Project \"" + myRoot + "\" report:");

        if (mySnapshot == null) {
            System.out.println("   no project history found");
        } else {
            if (structureChanged()) {
                System.out.println("   project structure change detected, rebuild required");
                moduleReport = false;
            }
        }

        if (moduleReport) {
            for (ModuleStatus mh : myPresent.myModuleHistories.values()) {
                System.out.println("   module " + mh.myName + " " + (mh.isOutdated(false) ? "is outdated" : "is up-to-date"));
                System.out.println("   module " + mh.myName + " tests " + (mh.isOutdated(true) ? "are outdated" : "are up-to-date"));
            }
        }
    }

    public void save() {
        saveSnapshot();
    }

    public void clean() {
        myProject.clean();
    }

    public void rebuild() {
        myProject.makeAll();
    }

    public void make(final boolean force, final boolean tests) {
        if (structureChanged() && !force) {
            System.out.println("Project \"" + myRoot + "\" structure changed, performing rebuild.");
            rebuild();
            return;
        }

        final List<Module> modules = new ArrayList<Module>();

        for (Map.Entry<String, ModuleStatus> entry : myPresent.myModuleHistories.entrySet()) {
            if (entry.getValue().isOutdated(tests))
                modules.add(myProject.getModules().get(entry.getKey()));
        }

        if (modules.isEmpty() && !force) {
            System.out.println("All modules are up-to-date.");
            return;
        }

        System.out.println("Rebuilding modules:");

        for (Module m : modules)
            System.out.println("  " + m.getName());

        makeModules(modules, tests);
    }

    private void makeModules(final List<Module> initial, final boolean tests) {
        final Set<Module> modules = new HashSet<Module>();
        final Map<Module, Set<Module>> reversedDependencies = new HashMap<Module, Set<Module>> ();

        for (Module m : myProject.getModules().values()) {
            for (Module.ModuleDependency mdep : m.getDependencies()) {
                final ClasspathItem cpi = mdep.getItem();

                if (cpi instanceof Module) {
                    Set<Module> sm = reversedDependencies.get(cpi);

                    if (sm == null) {
                        sm = new HashSet<Module> ();
                        reversedDependencies.put((Module) cpi, sm);
                    }

                    sm.add(m);
                }
            }
        }

        new Object() {
            public void run(final Collection<Module> initial) {
                if (initial == null)
                    return;

                for (Module module : initial) {
                    if (modules.contains(module))
                        continue;

                    modules.add(module);

                    run(reversedDependencies.get(module));
                }
            }
        }.run(initial);

        myProject.makeSelected(modules, tests);
    }

    public void makeModule(final String modName, final boolean force, final boolean tests) {
        final Module module = myProject.getModules().get(modName);
        final List<Module> list = new ArrayList<Module>();

        list.add(module);

        if (module == null) {
            System.err.println("Module \"" + modName + "\" not found in project \"" + myRoot + "\"");
            return;
        }

        if (structureChanged() && !force) {
            System.out.println("Project \"" + myRoot + "\" structure changed, performing rebuild.");
            rebuild();
            return;
        }

        final ModuleStatus h = myPresent.myModuleHistories.get(modName);
        if (h != null && !h.isOutdated(tests) && !force) {
            System.out.println("Module \"" + modName + "\" in project \"" + myRoot + "\" is up-to-date.");
            return;
        }

        makeModules(list, tests);
    }
}
