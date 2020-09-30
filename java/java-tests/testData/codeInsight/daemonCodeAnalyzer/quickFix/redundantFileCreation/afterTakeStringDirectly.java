// "Fix all 'Redundant File object creation' problems in file" "true"
import java.io.*;
import java.util.Formatter;

class Main {
  public void main(String[] args) throws IOException {
    new FileInputStream("in.txt");
    new FileOutputStream("in.txt");
    new FileReader("in.txt");
    new FileWriter("in.txt");
    new PrintStream("in.txt");
    new PrintWriter("in.txt");
    new Formatter("in.txt");
  }
}
