import java.util.*;
import org.jetbrains.annotations.*;

public class MutabilityInferred {
  @Nullable
  static List<String> getListNullOrImmutable(int x) {
    if(x == 0) return null;
    return Collections.singletonList("foo");
  }

  static List<String> getListImmutable(int x) {
    if(x == 0) return Collections.singletonList("bar");
    return Collections.singletonList("foo");
  }

  static List<String> getListImmutableOrMutable(int x) {
    if(x == 0) return new ArrayList<>();
    return Collections.singletonList("foo");
  }

  static native List<String> getListUnknown(int x);

  @Nullable
  static List<String> getListVar(boolean b) {
    List<String> list = getListUnknown(0);
    if (list != null) return list;
    if (b) return Collections.singletonList("xyz");
    return null;
  }

  static Map<String, String> getMap() {
    return Map.of("foo", "bar");
  }

  static Map.Entry<String, String> getEntry() {
    return Map.entry("foo", "bar");
  }

  void test() {
    getListNullOrImmutable(0).<warning descr="Immutable object is modified"><warning descr="Method invocation 'add' may produce 'java.lang.NullPointerException'">add</warning></warning>("a");
    getListImmutable(0).<warning descr="Immutable object is modified">add</warning>("b");
    getListImmutableOrMutable(0).add("c");
    getListVar(false).<warning descr="Method invocation 'add' may produce 'java.lang.NullPointerException'">add</warning>("d");
    getMap().<warning descr="Immutable object is modified">put</warning>("baz", "qux");
    getEntry().<warning descr="Immutable object is modified">setValue</warning>("qux");
  }
}
