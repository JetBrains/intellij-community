/*
Value is always true (obj instanceof Cloneable; line#12)
  An object type is exactly int[] which is a subtype of Cloneable (obj; line#12)
    Type of 'obj' is known from line #11 (c == obj.getClass(); line#11)
 */
import java.util.stream.Stream;

class A
  public void foo(Object obj) {
    if (Stream.of(CharSequence.class, Number.class, int[].class)
      .noneMatch(c -> c == obj.getClass())) return;
    if (<selection>obj instanceof Cloneable</selection>) {}
  }
}