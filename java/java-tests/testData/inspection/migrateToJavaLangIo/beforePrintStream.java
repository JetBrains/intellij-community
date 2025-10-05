import java.io.PrintStream;

public static void main(String[] args) {
  PrintStream stream = getSome();
  stream.<caret>println("Hello");
}


private static native PrintStream getSome();