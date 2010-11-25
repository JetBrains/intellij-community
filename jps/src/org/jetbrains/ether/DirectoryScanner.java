package org.jetbrains.ether;

import javax.xml.transform.Result;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 2:01
 * To change this template use File | Settings | File Templates.
 */
public class DirectoryScanner {
    private static FileFilter myDirectoryFilter = new FileFilter() {
        public boolean accept (File f) {
            final String name = f.getName();

            return f.isDirectory() && !name.equals(".") && !name.equals("..") ;
        }
    };

    private static FileFilter filterByExtensions (final String[] exts) {
        return new FileFilter (){
            public boolean accept (File path) {
                final String filename = path.getName();

                for (int i = 0; i<exts.length; i++) {
                    if (filename.endsWith(exts[i]))
                        return true;
                }

                return false;
            }
        };
    }

    public static class Result {
        public List<String> myFiles = new ArrayList<String> ();
        public long myEarliest = Long.MAX_VALUE;
        public long myLatest = 0;
    }

    public static Result getFiles (final String root) {
        final Result result = new Result ();

        new Object(){
            public void run (File root) {
                if (root.exists()) {
                  final File[] files = root.listFiles();

                  for (int i = 0; i<files.length; i++) {
                      long t = files[i].lastModified();

                      if (t > result.myLatest)
                        result.myLatest = t;

                      if (t < result.myEarliest)
                        result.myEarliest = t;

                      result.myFiles.add(files[i].getAbsolutePath());
                  }

                  final File[] subdirs = root.listFiles(myDirectoryFilter);

                  for (int i=0; i<subdirs.length; i++) {
                      run (subdirs [i]);
                  }
                }
            }
        }.run(new File (root));

        return result;
    }
}
