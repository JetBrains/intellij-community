// "Replace with 'boxed'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class Main {
  public static void main(String[] args) {
    List<Double> list = DoubleStream.of(1,2,3,4).m<caret>apToObj((x) -> (Double.valueOf(x))).collect(Collectors.toList());
  }
}