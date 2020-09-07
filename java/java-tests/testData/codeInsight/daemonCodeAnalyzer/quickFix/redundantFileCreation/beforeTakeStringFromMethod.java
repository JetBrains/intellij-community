// "Fix all 'Redundant File object creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  private static String getSomePathname() {
    return "Some pathname";
  }

  public void main(String[] args) throws IOException {
    new FileInputStream(new Fi<caret>le(getSomePathname()));
    new FileOutputStream(new File(getSomePathname()));
    new FileReader(new File(getSomePathname()));
    new FileWriter(new File(getSomePathname()));
    new PrintStream(new File(getSomePathname()));
    new PrintWriter(new File(getSomePathname()));
    new Formatter(new File(getSomePathname()));
  }
}
