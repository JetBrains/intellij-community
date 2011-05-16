package org.jetbrains.ether;

import com.sun.corba.se.spi.ior.MakeImmutable;
import org.apache.tools.ant.types.Description;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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

    private enum Action {
        REBUILD,
        MAKE,
        CLEAN,
        NONE
    }

    static {
        final Options.Descriptor[] descrs = {
                new Options.Descriptor("script", "script", "s", Options.ArgumentSpecifier.MANDATORY, "specify script for project library/sdk setup; use prefix '@' to specify script file"),
                new Options.Descriptor("inspect", "inspect", "i", Options.ArgumentSpecifier.OPTIONAL, "list relevant information for the whole project or specified module"),
                new Options.Descriptor("save", "save", "s", Options.ArgumentSpecifier.NONE, "collect and save project information"),
                new Options.Descriptor("clean", "clean", "c", Options.ArgumentSpecifier.NONE, "clean project"),
                new Options.Descriptor("rebuild", "rebuild", "r", Options.ArgumentSpecifier.NONE, "rebuild project"),
                new Options.Descriptor("make", "make", "m", Options.ArgumentSpecifier.OPTIONAL, "make the whole project or specified module"),
                new Options.Descriptor("tests", "tests", "t", Options.ArgumentSpecifier.NONE, "make tests as well"),
                new Options.Descriptor("force", "force", "f", Options.ArgumentSpecifier.NONE, "force actions"),
                new Options.Descriptor("incremental", "incremental", "i", Options.ArgumentSpecifier.NONE, "perform incremental make"),
                new Options.Descriptor("help", "help", "h", Options.ArgumentSpecifier.NONE, "show help on options"),
                new Options.Descriptor("debug", "debug", "d", Options.ArgumentSpecifier.NONE, "debug info (for development purposes)")
        };

        myOptions = new Options(descrs);
    }

    private static boolean doDebug() {
        return myOptions.get("debug") instanceof Options.Switch;
    }

    private static boolean doSave() {
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

    private static boolean doIncremental() {
            return myOptions.get("incremental") instanceof Options.Switch;
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

    private static String getScript() {
        final Options.Argument arg = myOptions.get("script");

        if (arg instanceof Options.Value)
            return ((Options.Value) arg).get();

        return null;
    }

    private static ProjectWrapper.Flags getFlags () {
        return new ProjectWrapper.Flags() {
            public boolean tests () {
                return doTests();
            }

            public boolean force () {
                return doForce();
            }

            public boolean incremental () {
                return doIncremental();
            }
        };
    }

    private static void checkConsistency() {
        int test = 0;

        if (doClean()) test++;
        if (doMake() != null) test++;
        if (doRebuild()) test++;

        if (test > 1) {
            System.err.print("WARNING: Conflicting options (should be --make OR --rebuild OR --clean); preferring ");
            if (doMake() != null)
                System.err.println("make.");
            else if (doRebuild())
                System.err.println("rebuild.");
        }

        if (doClean() || doRebuild()) {
            if (doTests()) {
                System.err.println("WARNING: extra --tests option ignored.");
            }
            if (doForce()) {
                System.err.println("WARNING: extra --force option ignored.");
            }
        }
    }

    private static Action getAction() {
        if (doMake() != null) {
            return Action.MAKE;
        }

        if (doRebuild())
            return Action.REBUILD;

        if (doClean())
            return Action.CLEAN;

        return Action.NONE;
    }

    public static void main(String[] args) {
        System.out.println("JetBrains.com build server. (C) JetBrains.com, 2010-2011.\n");

        final List<String> notes = myOptions.parse(args);

        for (String note : notes) {
            System.err.println("WARNING: " + note);
        }

        checkConsistency();

        if (doHelp()) {
            System.out.println("Usage: ??? <options> <project-specifier>\n");
            System.out.println("Options are:");
            System.out.println(myOptions.memo());
        }

        final List<String> projects = myOptions.getFree();

        if (projects.isEmpty() && !doHelp()) {
            System.out.println("Nothing to do; use --help or -h option to see the help.\n");
        }

        if (doDebug()) {
            DotPrinter.setPrintStream(System.out);
        }

        for (String prj : projects) {
            boolean saved = false;
            ProjectWrapper project = null;

            switch (getAction()) {
                case CLEAN:
                    System.out.println("Cleaning project \"" + prj + "\"");
                    project = ProjectWrapper.load(prj, getScript());

                    project.clean();
                    project.save();
                    saved = true;
                    break;

                case REBUILD:
                    System.out.println("Rebuilding project \"" + prj + "\"");
                    project = ProjectWrapper.load(prj, getScript());
                    project.rebuild();
                    project.save();
                    saved = true;
                    break;

                case MAKE:
                    final Options.Argument make = doMake();

                    if (make instanceof Options.Value) {
                        final String module = ((Options.Value) make).get();
                        System.out.println("Making module \"" + module + "\" in project \"" + prj + "\"");
                        project = ProjectWrapper.load(prj, getScript());
                        project.makeModule(module, getFlags ());
                        project.save();
                        saved = true;
                    } else if (make instanceof Options.Switch) {
                        System.out.println("Making all modules in project \"" + prj + "\"");
                        project = ProjectWrapper.load(prj, getScript());
                        project.makeModule(null, getFlags ());
                        project.save();
                        saved = true;
                    }
                    ;
                    break;
            }

            final Options.Argument inspect = doInspect();

            if (inspect instanceof Options.Switch) {
                project = ProjectWrapper.load(prj, getScript());
                project.report();
                if (doSave()) {
                    project.save();
                    saved = true;
                }
            } else if (inspect instanceof Options.Value) {
                project = ProjectWrapper.load(prj, getScript());
                project.report(((Options.Value) inspect).get());
                if (doSave()) {
                    project.save();
                    saved = true;
                }
            }

            if (doSave() && !saved) {
                project = ProjectWrapper.load(prj, getScript());
                project.save();
            }
        }
    }
}
