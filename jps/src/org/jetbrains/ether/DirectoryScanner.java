package org.jetbrains.ether;

import javax.xml.transform.Result;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 2:01
 * To change this template use File | Settings | File Templates.
 */

public class DirectoryScanner {

    public static class Result {
        final Set<ProjectWrapper.FileWrapper> myFiles;
        long myLatest;
        long myEarliest;

        public Result () {
            myFiles = new HashSet<ProjectWrapper.FileWrapper> ();
            myLatest = 0;
            myEarliest = Long.MAX_VALUE;
        }

        public void update (final ProjectWrapper.FileWrapper w) {
            final long t = w.getStamp ();

            if (t > myLatest)
                myLatest = t;

            if (t< myEarliest)
                myEarliest = t;

            myFiles.add(w);
        }

        public long getEarliest () {
            return myEarliest;
        }

        public long getLatest () {
            return myLatest;
        }

        public Set<ProjectWrapper.FileWrapper> getFiles () {
            return myFiles;
        }
    }

    private static class Crawler {
        final Result myResult;
        final FileFilter myFilter;
        final ProjectWrapper myProjectWrapper;

        public Crawler (final FileFilter ff, final ProjectWrapper pw) {
            myResult = new Result();
            myFilter = ff;
            myProjectWrapper = pw;
        }

        public Result getResult () {
            return myResult;
        }

        public void run(File root) {
            if (root.exists()) {
                final File[] files = root.listFiles(myFilter);

                for (int i = 0; i < files.length; i++) {
                    myResult.update (myProjectWrapper.new FileWrapper(files[i]));
                }

                final File[] subdirs = root.listFiles(myDirectoryFilter);

                for (int i = 0; i < subdirs.length; i++) {
                    run(subdirs[i]);
                }
            }
        }
    }

    private static FileFilter myDirectoryFilter = new FileFilter() {
        public boolean accept(File f) {
            final String name = f.getName();

            return f.isDirectory() && !name.equals(".") && !name.equals("..");
        }
    };

    private static FileFilter myTrueFilter = new FileFilter() {
        public boolean accept(File s) {
            return s.isFile();
        }
    };

    private static FileFilter createFilter(final Collection<String> excludes) {
        if (excludes == null) {
            return myTrueFilter;
        }

        StringBuffer buf = new StringBuffer();

        for (String exclude : excludes) {
            StringBuffer alternative = new StringBuffer();

            if (exclude != null) {
                for (int i = 0; i < exclude.length(); i++) {
                    final char c = exclude.charAt(i);

                    switch (c) {
                        case '.':
                        case '\\':
                        case '*':
                        case '^':
                        case '$':
                        case '[':
                        case '(':
                        case '|':
                            alternative.append("\\");
                            alternative.append(c);
                            break;

                        case '?':
                            alternative.append(".");
                            break;

                        default:
                            alternative.append(c);
                    }
                }
            }

            if (alternative.length() > 0) {
                alternative.append(".*");

                if (buf.length() > 0)
                    buf.append("|");

                buf.append("(");
                buf.append(alternative);
                buf.append(')');
            }
        }

        if (buf.length() > 0) {
            final Pattern patt = Pattern.compile(buf.toString());

            return new FileFilter() {
                public boolean accept(File f) {
                    final Matcher m = patt.matcher(f.getAbsolutePath());
                    final boolean ok = !m.matches();

                    return ok && f.isFile();
                }
            };
        }

        return myTrueFilter;
    }

    public static Result getFiles(final Set<String> roots, final Set<String> excludes, final ProjectWrapper pw) {
        final Crawler cw = new Crawler(createFilter(excludes), pw);

        for (String root : roots)
            cw.run(new File(pw.getAbsolutePath(root)));

        return cw.getResult();
    }
}
