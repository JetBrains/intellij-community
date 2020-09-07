// "Fix all 'Redundant file creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  public main(String[] args) {
    InputStream is = new FileInputStream("1.txt");
    OutputStream os = new FileOutputStream("2.txt");
    FileReader fr = new FileReader("3.txt");
    FileWriter fw = new FileWriter("4.txt");
    PrintStream ps = new PrintStream("5.txt");
    PrintWriter pw = new PrintWriter("6.txt");
    Formatter f = new Formatter("7.txt");
  }
}
