import java.util.stream.*;

public class ClassCastMethodReference {
  public static void main(String[] args) {
    Stream.of(args)
      .map(<warning descr="Cast is redundant">String.class::cast</warning>)
      .collect(Collectors.toList());
  }
}