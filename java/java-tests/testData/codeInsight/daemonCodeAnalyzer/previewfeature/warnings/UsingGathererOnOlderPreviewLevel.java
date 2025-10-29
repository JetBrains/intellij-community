import <warning descr="java.util.stream.Gatherers was in preview in JDK 22">java.util.stream.Gatherers</warning>;
import java.util.stream.Stream;

class Main {
  public static void main(String[] args) {
    <warning descr="java.util.stream.Stream#gather was in preview in JDK 22">Stream.of(1, 2, 3).gather</warning>(<warning descr="java.util.stream.Gatherers was in preview in JDK 22">Gatherers</warning>.windowFixed(3)).forEach(System.out::println);
  }
}