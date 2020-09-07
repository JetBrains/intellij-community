// "Fix all 'Redundant file creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  private static String getSomePathname() {
    return "Some pathname";
  }

  public main(String[] args) {
    InputStream is = new FileInputStream(getSomePathname());
    OutputStream os = new FileOutputStream(getSomePathname());
    FileReader fr = new FileReader(getSomePathname());
    FileWriter fw = new FileWriter(getSomePathname());
    PrintStream ps = new PrintStream(getSomePathname());
    PrintWriter pw = new PrintWriter(getSomePathname());
    Formatter f = new Formatter(getSomePathname());
  }
}
