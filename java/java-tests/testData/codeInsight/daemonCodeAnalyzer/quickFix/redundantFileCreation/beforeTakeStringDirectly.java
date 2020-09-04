// "Fix all 'Redundant file creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  public main(String[] args) {
    InputStream is = new FileInputStream(new Fi<caret>le("1.txt"));
    OutputStream os = new FileOutputStream(new File("2.txt"));
    FileReader fr = new FileReader(new File("3.txt"));
    FileWriter fw = new FileWriter(new File("4.txt"));
    PrintStream ps = new PrintStream(new File("5.txt"));
    PrintWriter pw = new PrintWriter(new File("6.txt"));
    Formatter f = new Formatter(new File("7.txt"));
  }
}
