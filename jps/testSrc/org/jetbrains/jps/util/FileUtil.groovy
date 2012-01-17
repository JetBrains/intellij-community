package org.jetbrains.jps.util

/**
 * @author nik
 */
class FileUtil {
  private static Set<String> IGNORED = [/*"MANIFEST.MF", "META-INF"*/] as Set
  private static Set<String> IGNORED_TEXT_DIFFS = ["MANIFEST.MF"] as Set

  static String loadFileText(File file) throws IOException{
    InputStream stream = new FileInputStream(file);
    Reader reader = new InputStreamReader(stream);
    try{
      return new String(loadText(reader, (int)file.length()));
    }
    finally{
      reader.close();
    }
  }

  static char[] loadText(Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length){
      return chars;
    }
    else{
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  static boolean createParentDirs(File file) {
    if (!file.exists()) {
      String parentDirPath = file.getParent()
      if (parentDirPath != null) {
        final File parentFile = new File(parentDirPath)
        return parentFile.exists() && parentFile.isDirectory() || parentFile.mkdirs()
      }
    }
    return false
  }

  static boolean delete(File file){
    File[] files = file.listFiles();
    if (files != null) {
      for (File file1 : files) {
        if (!delete(file1)) return false;
      }
    }

    for (int i = 0; i < 10; i++){
      if (file.delete() || !file.exists()) return true;
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) {

      }
    }
    return false;
  }

  static def compareFiles(File file1, File file2, String relativePath) {
    int processed
    def name = file1.name
    if (name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".zip")) {
      def dir1 = ZipUtil.extractToTempDir(file1)
      def dir2 = ZipUtil.extractToTempDir(file2)
      processed = _compareDirectories(dir1, dir2, relativePath)
      delete(dir1)
      delete(dir2)
    }
    else {
      def len1 = file1.length()
      def len2 = file2.length()
      if (len1 != len2 && !IGNORED_TEXT_DIFFS.contains(file1.name)) {
        System.out.println("file length mismatch for $relativePath: #1.len=$len1, #2.len=$len2");
      }
      processed = 1
    }
    return processed
  }

  static def compareDirectories(File dir1, File dir2) {
    if (!dir1.exists()) {
      System.out.println("${dir1.absolutePath} doesn't exist");
      return
    }
    if (!dir2.exists()) {
      System.out.println("${dir2.absolutePath} doesn't exist");
      return
    }
    System.out.println("Comparing ${dir1.absolutePath}(#1) and ${dir2.absolutePath}(#2)...");
    def files = _compareDirectories(dir1, dir2, "")
    System.out.println("$files files processed.");
  }

  private static int _compareDirectories(File dir1, File dir2, String relativePath) {
    def processed = 0
    Set<String> dir2Files = dir2.listFiles()*.name as Set

    dir1.listFiles().each {File child1 ->
      File child2 = new File(dir2, child1.name)
      if (!child2.exists()) {
        if (!IGNORED.contains(child1.name)) {
          System.out.println("#1: $relativePath/${child1.name}");
        }
      }
      else {
        if (child1.isFile() && child2.isFile()) {
          processed += compareFiles(child1, child2, relativePath + "/" + child1.name)
        }
        else if (child1.isDirectory() && child2.isDirectory()) {
          processed += _compareDirectories(child1, child2, relativePath + "/" + child1.name)
        }
        else {
          System.out.println("type mismatch for $relativePath: #1 is ${child1.isDirectory() ? "dir" : "file"}, #2 is ${child2.isDirectory() ? "dir" : "file"}");
        }
      }

      dir2Files.remove(child1.name)
    }
    dir2Files.each {
      if (!IGNORED.contains(it)) {
        System.out.println("#2: $relativePath/$it");
      }
    }
    return processed
  }

  static File createTempDirectory(String prefix) {
    def output = File.createTempFile(prefix, "tmp")
    output.delete()
    output.mkdirs()
    return output
  }
}
