package org.jetbrains.ether;

import junit.framework.TestCase;
import junitx.framework.FileAssert;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 26.07.11
 * Time: 0:34
 * To change this template use File | Settings | File Templates.
 */
public abstract class IncrementalTestCase extends TestCase {
    private final String groupName;
    private final String baseDir;
    private final String tempDir = System.getProperty("java.io.tmpdir");
    private final String workDir;

    protected IncrementalTestCase(final String name) throws Exception {
        super(name);
        groupName = name;
        baseDir = "testData" + File.separator + "incremental" + File.separator; // + groupName;

        for (int i = 0; ; i++) {
            final File tmp = new File(tempDir + File.separator + "__temp__" + i);
            if (tmp.mkdir()) {
                workDir = tmp.getPath() + File.separator;
                break;
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        prepare();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        delete(new File(workDir));
    }

    private String getDir(final String prefix) {
        final String name = getName();

        assert (name.startsWith("test"));

        final String result = Character.toLowerCase(name.charAt("test".length())) + name.substring("test".length() + 1);

        return prefix + File.separator + groupName + File.separator + result;
    }

    private String getBaseDir() {
        return getDir(baseDir);
    }

    private String getWorkDir() {
        return getDir(workDir);
    }

    private void delete(final File file) throws Exception {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                delete(f);
            }
        }

        if (!file.delete())
            throw new IOException("could not delete file or directory " + file.getPath());

    }

    private void copy(final File input, final File output) throws Exception {
        if (input.isDirectory()) {
            if (output.mkdirs()) {
                for (File f : input.listFiles()) {
                    copy(f, new File(output.getPath() + File.separator + f.getName()));
                }
            } else throw new IOException("unable to create directory " + output.getPath());
        } else if (input.isFile()) {
            final FileReader in = new FileReader(input);
            final FileWriter out = new FileWriter(output);
            int c;

            while ((c = in.read()) != -1)
                out.write(c);

            in.close();
            out.close();
        }
    }

    private void prepare() throws Exception {
        copy(new File(getBaseDir()), new File(getWorkDir()));
    }

    private void modify() throws Exception {
        final File dir = new File(getBaseDir());
        final File[] files = dir.listFiles(new FileFilter() {
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(".java.new");
            }
        }
        );

        for (File input : files) {
            final String name = input.getName();
            final File output = new File(getWorkDir() + File.separator + "src" + File.separator + name.substring(0, name.length() - ".new".length()));
            copy(input, output);
        }
    }

    public void doTest() throws Exception {
        final ProjectWrapper first = ProjectWrapper.load(getWorkDir(), null);

        first.clean();
        first.rebuild();
        first.save();

        modify();

        final ProjectWrapper second = ProjectWrapper.load(getWorkDir(), null);
        final PrintStream stream = new PrintStream(new FileOutputStream(getWorkDir() + ".log"));

        second.makeModule(null, new ProjectWrapper.Flags() {
            public boolean tests() {
                return false;
            }

            public boolean incremental() {
                return true;
            }

            public boolean force() {
                return false;
            }

            public PrintStream logStream() {
                return stream;
            }
        });

        stream.close();

        FileAssert.assertEquals(new File (getBaseDir () + ".log"), new File (getWorkDir() + ".log"));
    }
}
