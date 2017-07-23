// "Replace with findFirst()" "true"

import java.util.stream.IntStream;

public class Test {
  public static void main(String[] args) {
    String s = "  hello  ";
    String res = s.trim();
    if(args.length == 0) {
        res = IntStream.range(0, s.length()).boxed().filter(x -> s.charAt(x) == 'l').findFirst().map(String::valueOf).orElse(res);
    }
    System.out.println(res);
  }
}
