import java.util.stream.IntStream;

class MyTest {

  {
    StringBuilder collected =
      IntStream.range(0, 10)
        .collect(StringBuilder::new, StringBuilder::app<caret>end, StringBuilder::append);
  }
}
