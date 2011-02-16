import java.util.*;

public class Test {
  static <T> List<T> asList(T... tt) {
    System.out.println(tt);
    return null;
  }

  @SafeVarargs
  static <T> List<T> asListSuppressed(T... tt) {
    System.out.println(tt);
    return null;
  }

  static List<String> asStringList(List<String>... tt) {
    return tt[0];
  }

  static List<?> asQList(List<?>... tt) {
    return tt[0];
  }

  static List<?> asIntList(int... tt) {
    System.out.println(tt);
    return null;
  }


  public static void main(String[] args) {
    <warning descr="Unchecked generics array creation for varargs parameter">asList</warning>(new ArrayList<String>());

    asListSuppressed(new ArrayList<String>());

    //noinspection unchecked
    asList(new ArrayList<String>());

    <warning descr="Unchecked generics array creation for varargs parameter">asStringList</warning>(new ArrayList<String>());

    asQList(new ArrayList<String>());
    asIntList(1);

    final ArrayList<String> list = new ArrayList<String>();
    <warning descr="Unchecked generics array creation for varargs parameter">asList</warning>(list);
  }
}