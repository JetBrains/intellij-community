import java.util.*;

class Test {
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

    ArrayList<String>[] arrayOfStrings = null;
    asList(arrayOfStrings);
    asList((ArrayList<String>[])null);

    //overload should be chosen before target type is known -> inference failure
    <error descr="Incompatible types. Found: 'java.util.List<java.util.ArrayList<java.lang.String>>', required: 'java.util.List<java.util.ArrayList<java.lang.String>[]>'">List<ArrayList<String>[]> arraysList = asList(arrayOfStrings);</error>
    System.out.println(arraysList);

    asListSuppressed(new ArrayList<String>());

    //noinspection unchecked
    asList(new ArrayList<String>());

    <warning descr="Unchecked generics array creation for varargs parameter">asStringList</warning>(new ArrayList<String>());

    asQList(new ArrayList<String>());
    asIntList(1);

    final ArrayList<String> list = new ArrayList<String>();
    <warning descr="Unchecked generics array creation for varargs parameter">asList</warning>(list);
  }

  public static <V> void join(V[] list) {
    Arrays.asList(list);
  }
}

class NoWarngs {
    static final SemKey<String> FILE_DESCRIPTION_KEY = <warning descr="Unchecked generics array creation for varargs parameter">SemKey.createKey</warning>("FILE_DESCRIPTION_KEY");

    void f() {
        OCM<String> o =
                new <warning descr="Unchecked generics array creation for varargs parameter">OCM<></warning>("", true, new Condition<String>(){
            @Override
            public boolean val(String s) {
                return false;
            }
        }, Condition.TRUE);
      System.out.println(o);
    }
}

class SemKey<T extends String> {
  private final String myDebugName;
  private final SemKey<? super T>[] mySupers;

  private SemKey(String debugName, SemKey<? super T>... supers) {
    myDebugName = debugName;
    System.out.println(myDebugName);
    mySupers = supers;
    System.out.println(mySupers);
  }

  public static <T extends String> SemKey<T> createKey(String debugName, SemKey<? super T>... supers) {
    return new SemKey<T>(debugName, supers);
  }

  public <K extends T> SemKey<K> subKey(String debugName, SemKey<? super T>... otherSupers) {
    if (otherSupers.length == 0) {
      return new <warning descr="Unchecked generics array creation for varargs parameter">SemKey<K></warning>(debugName, this);
    }
    return new SemKey<K>(debugName, append(otherSupers, this));
  }

  public static <T> T[] append(final T[] src, final T element) {
    return append(src, element, <warning descr="Unchecked cast: 'java.lang.Class<capture<?>>' to 'java.lang.Class<T>'">(Class<T>)src.getClass().getComponentType()</warning>);
  }

   public static <T> T[] append(T[] src, final T element, Class<T> componentType) {
    int length = src.length;
    T[] result = <warning descr="Unchecked cast: 'java.lang.Object' to 'T[]'">(T[])java.lang.reflect.Array.newInstance(componentType, length + 1)</warning>;
    System.arraycopy(src, 0, result, 0, length);
    result[length] = element;
    return result;
  }
}

interface Condition<T> {
   boolean val(T t);

   Condition TRUE = new Condition() {
       @Override
       public boolean val(Object o) {
           return true;
       }
   };
}
class OCM<T> {
    OCM(T s, boolean b, Condition<T>... c) {
      System.out.println(s);
      System.out.println(b);
      System.out.println(c);
    }

    OCM(T s, Condition<T>... c) {
      this(s, false, c);
    }
}

class TPSubstitution<T> {
    public void f(T... args) {
      System.out.println(args);
    }

    public void g() {
        new TPSubstitution<String>().f();
    }
}