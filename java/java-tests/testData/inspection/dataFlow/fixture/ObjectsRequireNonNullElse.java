import java.util.*;
import org.jetbrains.annotations.*;

public class ObjectsRequireNonNullElse {
  public int requireNonNullElse(@Nullable String str) {
    str = Objects.requireNonNullElse(str, "");
    if (<warning descr="Condition 'str == null' is always 'false'">str == null</warning>) {}
    return str.length();
  }

  void test2(String s) {
    String s1 = Objects.requireNonNullElse(s, "");
    if (!s1.equals(s)) {
      if (<warning descr="Condition '!s1.isEmpty()' is always 'false'">!<warning descr="Result of 's1.isEmpty()' is always 'true'">s1.isEmpty()</warning></warning>) {}
    }
  }

  void test3(@Nullable String s) {
    String s2 = Objects.requireNonNullElseGet(s, () -> "");
    if (<warning descr="Condition 's2 == null' is always 'false'">s2 == null</warning>) {}
  }

}
