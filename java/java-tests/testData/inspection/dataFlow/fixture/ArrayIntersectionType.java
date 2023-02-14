import org.jetbrains.annotations.*;
import java.util.*;
import java.util.function.*;
import java.io.Serializable;

public class ArrayIntersectionType {
  @SuppressWarnings("unchecked")
  static <T extends Object & Comparable<? super T>> void test(Set<T> set) {
    T[] arr = (T[])set.toArray();
  }

  void test() {
    Comparable[] result = compute(() -> Math.random() > 0.5 ? longs() : strings());
    if (result == null) {}
    if (result instanceof Number[]) {}
    if (result instanceof Integer[]) {}
    if (result instanceof Long[]) {}
    if (result instanceof String[]) {}
    if (<warning descr="Condition 'result instanceof Serializable[]' is redundant and can be replaced with a null check">result instanceof Serializable[]</warning>) {}
  }
  
  native Long[] longs();
  native String[] strings();
  
  <T> T compute(Supplier<T> supplier) {
    return supplier.get();
  }
}