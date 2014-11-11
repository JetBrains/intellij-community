// "Replace lambda with method reference" "false"
import java.io.PrintStream;
import java.util.function.BiConsumer;

class Test {
  {
    BiConsumer<PrintStream, String> printer = (printStream, x) -> get(printSt<caret>ream).println(x);
  }

  PrintStream get(PrintStream p) {return p;}

}
