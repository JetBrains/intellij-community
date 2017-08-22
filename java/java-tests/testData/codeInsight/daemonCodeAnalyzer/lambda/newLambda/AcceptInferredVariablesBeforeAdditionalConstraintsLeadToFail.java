import java.io.Reader;
import java.util.Arrays;
import java.util.function.Consumer;

class Test {

  public static void main(String[] args) {
    Iterable<Consumer<Reader>> i  = Arrays.asList(<error descr="Unhandled exception: IOException">(r) -> r.read()</error>);
    Iterable<Consumer<Reader>> i1 = Arrays.<Consumer<Reader>>asList((r) -> r.<error descr="Unhandled exception: java.io.IOException">read()</error>);
  }
}