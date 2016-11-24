// "Replace Stream API chain with loop" "true"

import java.util.SplittableRandom;
import java.util.stream.Stream;

public class Main {
  public static void main(String[] args) {
    int n1 = Stream.generate(() -> 500).limit(100).r<caret>educe(0, new SplittableRandom(1)::nextInt, Integer::sum);
    System.out.println(n1);
  }
}