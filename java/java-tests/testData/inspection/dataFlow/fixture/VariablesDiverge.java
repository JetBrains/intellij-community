import java.io.File;

class Some {
  private void findRepository(File file, boolean b) {
    System.out.println(file.getName());
    if (b) {
      File parent = file;
      while (parent != null) {
        parent = parent.getParentFile();
      }
      System.out.println(<warning descr="Method invocation 'parent.getName()' may produce 'java.lang.NullPointerException'">parent.getName()</warning>);
    }
    System.out.println(file.getName());
  }

}


