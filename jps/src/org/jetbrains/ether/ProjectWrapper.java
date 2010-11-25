package org.jetbrains.ether;

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
    // Original JPS Project
    private Project myProject;

    // Project directory
    private String myRoot;

    // Some predefined belongings
    private final String myHistoryName = ".history";

    // Project history
    private ProjectHistory myHistory;
    private ProjectHistory myPresent;

    public ProjectWrapper(final String prjDir) {
        myProject = new Project(new GantBinding());
        myRoot = prjDir;
    }

    private String getProjectHistoryFileName() {
        return myRoot + File.separator + myHistoryName;
    }

    private ProjectHistory loadHistory() {
        ProjectHistory result = null;

        try {
            FileInputStream fis = new FileInputStream(getProjectHistoryFileName());
            ObjectInputStream in = new ObjectInputStream(fis);

            result = (ProjectHistory) in.readObject();

            in.close();
        } catch (IOException e) {
            //e.printStackTrace();
        } catch (ClassNotFoundException c) {
            c.printStackTrace();
        }

        return result;
    }

    private void saveHistory() {
        final ProjectHistory history = HistoryCollector.collectHistory(myProject);

        try {
            FileOutputStream fos = new FileOutputStream(getProjectHistoryFileName());
            ObjectOutputStream out = new ObjectOutputStream(fos);

            out.writeObject(history);
            out.close();
        } catch (IOException e) {

        }
    }

    public void load() {
        IdeaProjectLoader.loadFromPath(myProject, myRoot);
        myHistory = loadHistory();
        myPresent = HistoryCollector.collectHistory(myProject);
    }

    public void report(final String module) {
        final ModuleHistory m = myPresent.myModuleHistories.get(module);

        if (m == null) {
            System.out.println("No module \"" + module + "\" found in project \"");
        } else {
            System.out.println("Module " + m.myName + " " + (m.isDirty() ? "is outdated" : "is up-to-date"));
        }
    }

    private boolean structureChanged() {
        if (myHistory == null)
            return true;

        return myPresent.structureChanged(myHistory);
    }

    public void report() {
        boolean moduleReport = true;

        System.out.println("Project \"" + myRoot + "\" report:");

        if (myHistory == null) {
            System.out.println("   no project history found");
        } else {
            if (structureChanged()) {
                System.out.println("   project structure change detected, rebuild required");
                moduleReport = false;
            }
        }

        if (moduleReport) {
            for (ModuleHistory mh : myPresent.myModuleHistories.values()) {
                System.out.println("   module " + mh.myName + " " + (mh.isDirty() ? "is outdated" : "is up-to-date"));
            }
        }
    }

    public void save() {
        saveHistory();
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

        for (Map.Entry<String, ModuleHistory> entry : myPresent.myModuleHistories.entrySet()) {
            if (entry.getValue().isDirty())
                modules.add(myProject.getModules().get(entry.getKey()));
        }

        if (modules.isEmpty() && !force) {
            System.out.println("All module are up-to-date.");
            return;
        }

        System.out.println("Rebuilding modules:");

        for (Module m : modules)
            System.out.println("  " + m.getName());

        makeModules(modules, tests);
    }

    private void makeModules(final List<Module> initial, final boolean tests) {
        final Set<Module> modules = new HashSet<Module>();

        new Object() {
            public void run(final List<Module> initial) {
                for (Module module : initial) {
                    if (modules.contains(module))
                        return;

                    modules.add(module);

                    final List<Module> successors = new ArrayList<Module>();

                    for (Module.ModuleDependency dep : module.getDependencies()) {
                        final ClasspathItem cpi = dep.getItem();

                        if (cpi instanceof Module) {
                            successors.add((Module) cpi);
                        }
                    }
                    run(successors);
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

        final ModuleHistory h = myPresent.myModuleHistories.get(modName);
        if (h != null && !h.isDirty() && !force) {
            System.out.println("Module \"" + modName + "\" in project \"" + myRoot + "\" is up-to-date.");
            return;
        }

        makeModules(list, tests);
    }
}
