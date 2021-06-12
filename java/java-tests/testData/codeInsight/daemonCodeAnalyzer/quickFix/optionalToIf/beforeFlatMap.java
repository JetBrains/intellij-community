// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  private void reusesVariable(String in) {
    Object out = Optional.of<caret>(in).flatMap(o -> Optional.of(o)).map(o -> id(o)).get();
  }

  private void checkIsRemoved(String in) {
    String out = Optional.of(in).flatMap(s -> Optional.of(in)).get();
  }

  void simple(String in) {
    String out = Optional.ofNullable<caret>(in).flatMap(s -> Optional.of(s)).orElse("bar");
  }

  void simpleWithMap(String in) {
    String out = Optional.ofNullable<caret>(in).flatMap(s -> Optional.of(s).map(v -> id(v))).orElse("bar");
  }

  void nested(String in) {
    String out = Optional.ofNullable(in).flatMap(s1 -> Optional.of(s1).flatMap(s2 -> Optional.of(s2))).orElse("bar");
  }

  void outer(String in, String p) {
    String out = Optional.ofNullable(in).flatMap(s1 -> Optional.of(s1).flatMap(s2 -> Optional.of(s2 + s1 + p))).orElse("bar");
  }

  void nullableOuter(String in, String p) {
    String out = Optional.ofNullable(in).flatMap(s1 -> Optional.of(p)).orElse("bar");
  }

  void nestedFlatMap(String var0) {
    boolean b = Optional.ofNullable(var0)
      .flatMap(var1 ->
                 Optional.of(var1).map(s -> s.toLowerCase())
                   .flatMap(var2 -> Optional.ofNullable(var2)))
      .isPresent();
  }

  void nestedFlatMapWithOuterFlatMapParam(String param0) {
    String result = Optional.of(param0).flatMap(var0 -> Optional.of("foo").flatMap(var1 -> Optional.of(var0))).get();
  }

  void nestedOr(String param0) {
    boolean result;
    result = Optional.of(param0)
      .flatMap(var0 -> Optional.<String>empty().or(() -> Optional.of(var0)))
      .isEmpty();
  }

  void flatMapsWithSameParamName(String param0) {
    Optional.of(param0)
      .flatMap(var0 -> Optional.of("foo").map(s -> ("foo").toLowerCase()))
      .flatMap(var0 -> Optional.of("bar")).<caret>get()
  }

  String flatMapWithOrInside() {
    return Optional.<String>empty().flatMap(s1 -> Optional.empty().or(() -> Optional.empty())).get();
  }

  <T> T id(T t) {
    return t;
  }
}