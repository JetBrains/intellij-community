
import java.io.File;

class Main {

  private static File[] <warning descr="Private method 'scanFolder(java.io.File)' is never used">scanFolder</warning>(File javasFolder) {
    return javasFolder.listFiles(Main::checkForJdk);
  }

  public static boolean checkForJdk(String <warning descr="Parameter 'homePath' is never used">homePath</warning>) {return false;}
  public static boolean checkForJdk(File homePath) {return false;}

}
