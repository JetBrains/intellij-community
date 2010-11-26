package org.jetbrains.ether;

import org.apache.tools.ant.types.Description;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 18.11.10
 * Time: 19:42
 * To change this template use File | Settings | File Templates.
 * <p/>
 * The main class to start with
 */
public class Main {
    private static final Options myOptions;

    static {
        final Options.Descriptor[] descrs = {
                new Options.Descriptor("inspect", "inspect", "i", Options.ArgumentSpecifier.OPTIONAL, "list relevant information for the whole project or specified module"),
                new Options.Descriptor("save", "save", "s", Options.ArgumentSpecifier.NONE, "collect and save project information"),
                new Options.Descriptor("clean", "clean", "c", Options.ArgumentSpecifier.NONE, "clean project"),
                new Options.Descriptor("rebuild", "rebuild", "r", Options.ArgumentSpecifier.NONE, "rebuild project"),
                new Options.Descriptor("make", "make", "m", Options.ArgumentSpecifier.OPTIONAL, "make the whole project or specified module"),
                new Options.Descriptor("tests", "tests", "t", Options.ArgumentSpecifier.NONE, "make tests as well"),
                new Options.Descriptor("force", "force", "f", Options.ArgumentSpecifier.NONE, "force actions"),
                new Options.Descriptor("help", "help", "h", Options.ArgumentSpecifier.NONE, "show help on options"),
                new Options.Descriptor("tests", "tests", "t", Options.ArgumentSpecifier.NONE, "make tests as well")
        };

        myOptions = new Options(descrs);
    }

    private static boolean doSave () {
        return myOptions.get("save") instanceof Options.Switch;
    }

    private static boolean doTests() {
        return myOptions.get("tests") instanceof Options.Switch;
    }

    private static boolean doRebuild() {
        return myOptions.get("rebuild") instanceof Options.Switch;
    }

    private static boolean doForce() {
        return myOptions.get("force") instanceof Options.Switch;
    }

    private static boolean doHelp() {
        return myOptions.get("help") instanceof Options.Switch;
    }

    private static Options.Argument doInspect() {
        return myOptions.get("inspect");
    }

    private static Options.Argument doMake() {
        return myOptions.get("make");
    }

    private static boolean doClean() {
        return myOptions.get("clean") instanceof Options.Switch;
    }

    public static void main(String[] args) {
        System.out.println("JetBrains.com build server. (C) JetBrains.com, 2010.\n");

        final List<String> notes = myOptions.parse(args);

        for (String note : notes) {
            System.err.println("Warning: " + note);
        }

        if (doHelp()) {
            System.out.println("Usage: ??? <options> <project-specifier>\n");
            System.out.println("Options are:");
            System.out.println(myOptions.memo());
        }

        final List<String> projects = myOptions.getFree();

        if (projects.isEmpty() && ! doHelp()) {
            System.out.println("Nothing to do; use --help or -h option to see the help.\n");
        }

        for (String prj : projects) {
            final ProjectWrapper project = new ProjectWrapper(prj);
            boolean saved = false;

            if (doClean()) {
                System.out.println("Cleaning project \"" + prj + "\"");
                project.load();
                project.clean();
                project.save();
                saved = true;
            }

            if (doRebuild()) {
                System.out.println("Rebuilding project \"" + prj + "\"");
                project.load();
                project.rebuild();
                project.save();
                saved = true;
            }

            final Options.Argument make = doMake();

            if (make instanceof Options.Value) {
                final String module = ((Options.Value) make).get();

                System.out.println("Making module \"" + module + "\" in project \"" + prj + "\"");
                project.load();
                project.makeModule(module, doForce(), doTests());
                project.save();
                saved = true;
            }
            else if (make instanceof Options.Switch) {
                System.out.println("Making project \"" + prj + "\"");
                project.load();
                project.make(doForce (), doTests());
                project.save();
                saved = true;
            }

            final Options.Argument inspect = doInspect();

            if (inspect instanceof Options.Switch) {
                project.load();
                project.report();
                if (doSave()) {
                    project.save();
                    saved = true;
                }
            } else if (inspect instanceof Options.Value) {
                project.load();
                project.report(((Options.Value) inspect).get());
                if (doSave()) {
                    project.save();
                    saved = true;
                }
            }

            if (doSave() && !saved) {
                project.load();
                project.save();
            }
        }
    }
}
