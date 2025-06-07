import java.io.FileInputStream;
import java.io.IOException;

public class <caret>IncompleteClass {
  public static void main(String[] args) throws IOException {
    try (var s = new FileInputStream(args[0]))