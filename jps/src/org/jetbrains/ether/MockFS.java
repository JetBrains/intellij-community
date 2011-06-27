package org.jetbrains.ether;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 27.06.11
 * Time: 21:03
 * To change this template use File | Settings | File Templates.
 */
public class MockFS {
    final Map<java.io.File, File> myFiles = new HashMap<java.io.File, File>();
    final File myRoot;

    private File getFile(java.io.File f) {
        if (f.getAbsolutePath().startsWith(myRoot.getAbsolutePath())) {
            File g = myFiles.get(f);

            if (g != null) {
                return g;
            }

            g = new File(f, false);

            myFiles.put(f, g);

            return g;
        }

        return null;
    }

    public class File extends java.io.File {
        final Set<File> mySubFiles = new HashSet<File>();
        final File myParent;

        private File(final java.io.File file, final boolean root) {
            super(file.getPath());

            if (root) {
                myParent = null;
                return;
            }

            final java.io.File parent = file.getParentFile();

            myParent = parent == null ? null : getFile(parent);

            if (myParent != null) {
                myParent.mySubFiles.add(this);
            }
        }

        @Override
        public String[] list() {
            return list(new FilenameFilter() {
                public boolean accept(java.io.File dir, String name) {
                    return true;
                }
            });
        }

        @Override
        public String[] list(final FilenameFilter filter) {
            final List<String> result = new LinkedList<String>();

            for (final File f : mySubFiles) {
                if (filter.accept(this, f.getName())) {
                    result.add(f.getName());
                }
            }

            return result.toArray(new String[result.size()]);
        }

        @Override
        public java.io.File[] listFiles() {
            return mySubFiles.toArray(new File[mySubFiles.size()]);
        }

        @Override
        public java.io.File[] listFiles(final FilenameFilter filter) {
            final List<File> result = new LinkedList<File>();

            for (File f : mySubFiles) {
                if (filter.accept(this, f.getName())) {
                    result.add(f);
                }
            }

            return result.toArray(new File[result.size()]);
        }

        @Override
        public java.io.File[] listFiles(final FileFilter filter) {
            final List<File> result = new LinkedList<File>();

            for (File f : mySubFiles) {
                if (filter.accept(f)) {
                    result.add(f);
                }
            }

            return result.toArray(new File[result.size()]);
        }
    }

    private MockFS(final String root) {
        final java.io.File rootFile = new java.io.File(root);

        myRoot = new File(rootFile, true);
        myFiles.put(rootFile, myRoot);
    }

    public static File fromFiles(final String root, final Collection<String> files) {
        final MockFS fs = new MockFS(root);

        for (String f : files) {
            fs.getFile(new java.io.File (f));
        }

        return fs.myRoot;
    }
}
