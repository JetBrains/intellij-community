// "Replace lambda with method reference" "true"
import java.io.PrintStream;
import java.util.function.BiConsumer;

class Test {
  {
    BiConsumer<PrintStream, String> printer = (printStream, x) -> printSt<caret>ream.println(x);
  }


}
