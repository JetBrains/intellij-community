// "Fix all 'Redundant File object creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  public void main(String[] args) throws IOException {
    new FileInputStream(new Fi<caret>le("in.txt"));
    new FileOutputStream(new File("in.txt"));
    new FileReader(new File("in.txt"));
    new FileWriter(new File("in.txt"));
    new PrintStream(new File("in.txt"));
    new PrintWriter(new File("in.txt"));
    new Formatter(new File("in.txt"));
  }
}
