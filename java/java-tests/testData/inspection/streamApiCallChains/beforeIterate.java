// "Replace with three-arg 'iterate()'" "true-preview"
import java.util.stream.*;

class X {
  public static void main(String[] args) {
    IntStream.iterate(0, i -> i + 1).
      takeW<caret>hile(i -> i < 10).forEach(System.out::println);
  }
}