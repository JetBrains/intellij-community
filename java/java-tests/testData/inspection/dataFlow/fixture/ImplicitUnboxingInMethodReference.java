import org.jetbrains.annotations.*;
import java.util.stream.*;

// IDEA-250913
class Test {
  void main() {
    print(<warning descr="Unboxing of 'parse(\"1\")' may produce 'NullPointerException'">parse("1")</warning>);

    Stream.of("1", "2")
      .map(this::parse)
      .forEach(<warning descr="Passing an argument to the method reference requires unboxing which may produce 'NullPointerException'">this::print</warning>);

  }

  @Nullable
  Long parse(String s) {
    return null;
  }

  private void print(long s) {
    System.out.println(s);
  }
}