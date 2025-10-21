import java.io.BufferedReader;
import java.io.FileReader;

public class Main {
  public static void main(String[] args) {
    BufferedReader reader = new BufferedReader(new FileReader("file.txt"));.<caret>
  }
}