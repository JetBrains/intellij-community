import java.util.Arrays;
import java.util.stream.DoubleStream;

class Test {
  DoubleStream m(double[] a) {
    return Arrays.stream(a);<caret>
  }
}