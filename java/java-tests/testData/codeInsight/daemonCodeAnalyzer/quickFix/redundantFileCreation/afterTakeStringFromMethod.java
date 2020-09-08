// "Fix all 'Redundant File object creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  private static String getSomePathname() {
    return "Some pathname";
  }

  public void main(String[] args) throws IOException {
    new FileInputStream(getSomePathname());
    new FileOutputStream(getSomePathname());
    new FileReader(getSomePathname());
    new FileWriter(getSomePathname());
    new PrintStream(getSomePathname());
    new PrintWriter(getSomePathname());
    new Formatter(getSomePathname());
  }
}
