package slow;

import java.util.Arrays;
import java.util.List;

class Pair<K, L> {

  static <S, T> Pair<S, T> create(S s, T t) {
    return null;
  }

  {
    List<Pair<String, String>> pairs = <warning descr="Vararg method call with 50+ poly arguments may cause compilation and analysis slowdown">Arrays.asList</warning>(
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""),
      Pair.create("", ""));
  }
}