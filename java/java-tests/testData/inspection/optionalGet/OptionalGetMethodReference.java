import java.util.stream.*;
import java.util.*;

public class OptionalGetMethodReference {
  public static void main(String[] args) {
    Stream.of(Optional.ofNullable(Math.random() > 0.5 ? 1 : null))
      .map(<warning descr="'Optional::get' without 'isPresent()' check">Optional::get</warning>)
      .forEach(System.out::println);

    Stream.of(Optional.ofNullable(Math.random() > 0.5 ? 1 : null))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .forEach(System.out::println);
  }
}