import java.io.*;

public class Simple {
  public long modificationTime(File file) {
    if (<info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.isDirectory()</info>) {
      System.out.println(<info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.isFile()</info>);
    }
    return <info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.lastModified()</info>;
  }
  public long isAlwaysDirectory(File file) {
    System.out.println(<info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.isFile()</info>);
    while (file.isDirectory()) {
      System.out.println(<info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.isFile()</info>);
      System.out.println(<info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.length()</info>);
    }
    return <info descr="Multiple file attribute calls can be replaced with single 'Files.readAttributes()' call">file.lastModified()</info>;
  }
}