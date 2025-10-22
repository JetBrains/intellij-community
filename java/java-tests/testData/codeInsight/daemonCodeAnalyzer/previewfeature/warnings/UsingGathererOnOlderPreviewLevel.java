import <warning descr="java.util.stream.Gatherers is a preview API and may be removed in a future release">java.util.stream.Gatherers</warning>;
import java.util.stream.Stream;

class Main {
  public static void main(String[] args) {
    <warning descr="java.util.stream.Stream#gather is a preview API and may be removed in a future release">Stream.of(1, 2, 3).gather</warning>(<warning descr="java.util.stream.Gatherers is a preview API and may be removed in a future release">Gatherers</warning>.windowFixed(3)).forEach(System.out::println);
  }
}