import java.io.*;
import java.util.*;

abstract class C {
  Iterator<? extends Comparable<? extends Serializable>> bar(List<String> x, Set<Integer> y) {
    return foo(x, y).iterator();
  }

  abstract <T> T foo(T x, T y);
}