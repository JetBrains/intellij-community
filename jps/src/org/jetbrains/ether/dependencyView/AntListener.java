package org.jetbrains.ether.dependencyView;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.04.11
 * Time: 21:31
 * To change this template use File | Settings | File Templates.
 */
public class AntListener implements BuildListener {
    private static final Pattern read = Pattern.compile("\\[parsing started (.*)\\]");
    private static final Pattern written = Pattern.compile("\\[wrote (.*)\\]");

    private final Map<String, List<String>> readFiles = new HashMap<String, List<String>>();
    private final Map<String, List<ClassFileInfo>> writtenFiles = new HashMap<String, List<ClassFileInfo>>();
    private final String targetFolder;
    private final int stripLength;
    private final List<String> sourceRoots;
    private final Callbacks.Backend callback;

    private static String basename(final String name, final String extension) {
        return name.substring(0, name.length() - extension.length());
    }

    private static ClassReader getClassReader(final String fullName) {
        try {
            final File f = new File(fullName);
            final byte[] buffer = new byte[(int) f.length()];
            final FileInputStream i = new FileInputStream(f);

            final int n = i.read(buffer);

            i.close();

            assert ((int) f.length() == n);

            return new ClassReader(buffer);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return null;
        }
    }

    private class ClassFileInfo {
        final String packageName;
        final String fullName;
        List<String> subClasses = null;

        public ClassFileInfo(final String fullName, final String packageName) {
            this.packageName = packageName;
            this.fullName = fullName;
        }

        public void attachSubClass(final String name) {
            if (subClasses == null) {
                subClasses = new LinkedList<String>();
            }

            subClasses.add(name);
        }

        public String getFullName() {
            return fullName;
        }

        public String getPackageName() {
            return packageName;
        }

        public String toString() {
            final StringBuffer buffer = new StringBuffer();

            buffer.append("    Root: " + fullName + ", package " + packageName + "\n");
            buffer.append("    Subclasses:");

            if (subClasses != null)
                for (String s : subClasses) {
                    buffer.append(" " + s);
                }

            buffer.append(".\n");

            return buffer.toString();
        }

        public void associate(final Callbacks.SourceFileNameLookup sourceFileNameLookup) {
            if (callback != null) {
                callback.associate(fullName, sourceFileNameLookup, getClassReader(fullName));

                if (subClasses != null) {
                    for (String s : subClasses) {
                        callback.associate(s, sourceFileNameLookup, getClassReader(s));
                    }
                }
            }
        }
    }

    public AntListener(final String targetFolder, final List<String> sourceRoots, final Callbacks.Backend callback) {
        this.targetFolder = targetFolder;
        this.sourceRoots = sourceRoots;
        this.callback = callback;
        stripLength = targetFolder.length() + 1;
    }

    public void buildStarted(BuildEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void buildFinished(BuildEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void targetStarted(BuildEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void targetFinished(BuildEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void taskStarted(BuildEvent event) {
    }

    public void taskFinished(BuildEvent event) {
        if (event.getTask().getTaskName().equals("javac")) {
            for (Map.Entry<String, List<ClassFileInfo>> e : writtenFiles.entrySet()) {
                final String className = e.getKey();

                final List<ClassFileInfo> infos = e.getValue();
                final List<String> read = readFiles.get(className);

                if (infos.size() == 1 && read != null && read.size() == 1) {
                    infos.get(0).associate(Callbacks.getDefaultLookup(read.get(0)));
                    continue;
                }

                process:
                for (ClassFileInfo c : infos) {
                    final String packageName = c.getPackageName();

                    final Callbacks.SourceFileNameLookup sourceNameLookup = new Callbacks.SourceFileNameLookup() {
                        public String get(final String sourceAttribute) {

                            assert (sourceAttribute != null);

                            final String className = basename(sourceAttribute, ".java");
                            final List<String> candidates = readFiles.get(className);

                            assert (candidates != null);

                            if (candidates.size() == 1) {
                                return candidates.get(0);
                            }

                            for (String r : candidates) {
                                if (new PackageNameSelector().get(r).equals(packageName)) {
                                    return r;
                                }
                            }

                            assert (false);

                            return null;
                        }
                    };

                    c.associate(sourceNameLookup);
                }
            }

            /*
            System.out.println("Written files info:");

            for (Map.Entry<String, List<ClassFileInfo>> e : writtenFiles.entrySet()) {
                final String className = e.getKey();
                final List<ClassFileInfo> infos = e.getValue();

                System.out.println("  Class name: " + className);
                System.out.println("  Class file info(s): ");

                for (ClassFileInfo c : infos) {
                    System.out.println(c.toString());
                }
            }

            System.out.println("End.");

            System.out.println("Read files info:");

            for (Map.Entry<String, List<String>> e : readFiles.entrySet()) {
                final String className = e.getKey();
                final List<String> files = e.getValue();

                System.out.println("  Class name: " + className);
                System.out.print("  Files:");

                for (String file : files) {
                    System.out.print(" " + file);
                }

                System.out.println(".");
            }
            System.out.println("End.");
            */
        }
    }

    private String extractPackage(final String s) {
        if (s.length() < stripLength)
            return "";

        return s.substring(stripLength);
    }

    public void messageLogged(BuildEvent event) {
        final String m = event.getMessage();
        final Matcher writtenMatcher = written.matcher(m);
        final Matcher readMatcher = read.matcher(m);

        if (writtenMatcher.matches()) {
            final String fullName = writtenMatcher.group(1);
            final File file = new File(fullName);

            final String classFileName = file.getName();
            final String packageName = extractPackage(fullName.substring(0, fullName.length() - classFileName.length() - 1));

            String className = basename(classFileName, ".class");
            boolean subClass = false;

            final int i = className.indexOf('$');

            if (i >= 0) {
                className = className.substring(0, i);
                subClass = true;
            }

            List<ClassFileInfo> info = writtenFiles.get(className);


            if (info == null) {
                info = new LinkedList<ClassFileInfo>();
                writtenFiles.put(className, info);
            }

            boolean updated = false;

            for (ClassFileInfo ci : info) {
                if (subClass) {
                    if (ci.getPackageName().equals(packageName)) {
                        ci.attachSubClass(fullName);
                        updated = true;
                    }
                } else {
                    if (ci.getFullName().equals(fullName)) {
                        updated = true;
                        break;
                    }
                }
            }

            if (!updated) {
                if (subClass) {
                    final ClassFileInfo c = new ClassFileInfo(targetFolder + File.separator + packageName + File.separator + className + ".class", packageName);
                    c.attachSubClass(fullName);
                    info.add(c);
                } else {
                    info.add(new ClassFileInfo(fullName, packageName));
                }
            }
        }

        if (readMatcher.matches()) {
            final String fullName = readMatcher.group(1);
            final File file = new File(fullName);
            final String sourceFileName = file.getName();
            final String className = basename(sourceFileName, ".java");

            List<String> files = readFiles.get(className);

            if (files == null) {
                files = new LinkedList<String>();
                readFiles.put(className, files);
            }

            files.add(fullName);
        }
    }
}
