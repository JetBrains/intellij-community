// "Fix all 'Redundant file creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  private static String getSomePathname() {
    return "Some pathname";
  }

  public main(String[] args) {
    InputStream is = new FileInputStream(new Fi<caret>le(getSomePathname()));
    OutputStream os = new FileOutputStream(new File(getSomePathname()));
    FileReader fr = new FileReader(new File(getSomePathname()));
    FileWriter fw = new FileWriter(new File(getSomePathname()));
    PrintStream ps = new PrintStream(new File(getSomePathname()));
    PrintWriter pw = new PrintWriter(new File(getSomePathname()));
    Formatter f = new Formatter(new File(getSomePathname()));
  }
}
