import java.io.File;

class Some {
  private void findRepository(File file, boolean b) {
    System.out.println(file.getName());
    if (b) {
      File parent = file;
      while (parent != null) {
        parent = parent.getParentFile();
      }
      System.out.println(parent.<warning descr="Method invocation 'getName' may produce 'java.lang.NullPointerException'">getName</warning>());
    }
    System.out.println(file.getName());
  }

}


