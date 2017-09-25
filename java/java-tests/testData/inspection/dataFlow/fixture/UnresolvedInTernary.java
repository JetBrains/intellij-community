import java.util.*;

public class UnresolvedInTernary {
  void sideEffect() {
    System.out.println("side effect");
  }

  void sideEffectTest() {
    if(<error descr="Cannot resolve symbol 'test'">test</error>) {
      if(<error descr="Cannot resolve symbol 'test'"><warning descr="Condition 'test' is always 'true'">test</warning></error>) {
        sideEffect();
      }
      if(<error descr="Cannot resolve symbol 'test'">test</error>) {
        sideEffect();
      }
    }
  }

  List<?> getList() {
    return Arrays.asList(
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 1,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 2,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 3,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 4,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 5,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 6,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 7,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 8,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 9,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 10,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 11,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 12,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 13,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 14,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 15,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 16,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 17,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 18,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 19,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 20,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 21,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 22,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 23,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 24,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 25,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 26,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 27,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 28,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 29,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 30,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 31,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 32,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 33,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 34,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 35,
      <error descr="Cannot resolve symbol 'test'">test</error> ? null : 36,
      <error descr="Cannot resolve symbol 'test'">test</error> ?
        <error descr="Cannot resolve symbol 'test'"><warning descr="Condition 'test' is always 'true'">test</warning></error> ? 1 : 2 : 3
    );
  }
}