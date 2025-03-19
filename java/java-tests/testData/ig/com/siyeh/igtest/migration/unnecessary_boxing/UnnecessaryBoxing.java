package com.siyeh.igtest.migration.unnecessary_boxing;




public class UnnecessaryBoxing {

    Integer foo(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar;
    }

    public static void main(String[] args)
    {
        final Integer intValue = new <warning descr="Unnecessary boxing">Integer</warning>(3);
        final Long longValue = new <warning descr="Unnecessary boxing">Long</warning>(3L);
        final Long longValue2 = new <warning descr="Unnecessary boxing">Long</warning>(3);
        final Short shortValue = new <warning descr="Unnecessary boxing">Short</warning>((short)3);
        final Double doubleValue = new <warning descr="Unnecessary boxing">Double</warning>(3.0);
        final Float floatValue = new <warning descr="Unnecessary boxing">Float</warning>(3.0F);
        final Byte byteValue = new <warning descr="Unnecessary boxing">Byte</warning>((byte)3);
        final Boolean booleanValue = new <warning descr="Unnecessary boxing">Boolean</warning>(true);
        final Character character = new <warning descr="Unnecessary boxing">Character</warning>('c');
    }

    Integer foo2(String foo, int bar) {
        return foo == null ? Integer.<warning descr="Unnecessary boxing">valueOf</warning>(0) : bar;
    }

    void additionalInnerBoxing(String str) {
      short s = Short.<warning descr="Redundant boxing, 'Short.parseShort()' call can be used instead">valueOf</warning>(str);
      int i = Integer.<warning descr="Redundant boxing, 'Integer.parseInt()' call can be used instead">valueOf</warning>(str);
      long l = Long.<warning descr="Redundant boxing, 'Long.parseLong()' call can be used instead">valueOf</warning>(str);
      double d = Double.<warning descr="Redundant boxing, 'Double.parseDouble()' call can be used instead">valueOf</warning>(str);
      float f = Float.<warning descr="Redundant boxing, 'Float.parseFloat()' call can be used instead">valueOf</warning>(str);
      boolean bool = Boolean.<warning descr="Redundant boxing, 'Boolean.parseBoolean()' call can be used instead">valueOf</warning>(str);
      byte b = Byte.<warning descr="Redundant boxing, 'Byte.parseByte()' call can be used instead">valueOf</warning>(str);
    }

    short parseShort(String id) {
      return Short.<warning descr="Redundant boxing, 'Short.parseShort()' call can be used instead">valueOf</warning>(id);
    }

    int parseInt(String id) {
      return Integer.<warning descr="Redundant boxing, 'Integer.parseInt()' call can be used instead">valueOf</warning>(id);
    }

    long parseLong(String id) {
      return Long.<warning descr="Redundant boxing, 'Long.parseLong()' call can be used instead">valueOf</warning>(id);
    }

    double parseDouble(String id) {
      return Double.<warning descr="Redundant boxing, 'Double.parseDouble()' call can be used instead">valueOf</warning>(id);
    }

    float parseFloat(String id) {
      return Float.<warning descr="Redundant boxing, 'Float.parseFloat()' call can be used instead">valueOf</warning>(id);
    }

    boolean parseBoolean(String id) {
      return Boolean.<warning descr="Redundant boxing, 'Boolean.parseBoolean()' call can be used instead">valueOf</warning>(id);
    }

    byte parseByte(String id) {
      return Byte.<warning descr="Redundant boxing, 'Byte.parseByte()' call can be used instead">valueOf</warning>(id);
    }

    void noUnboxing(Object val) {
        if (val == Integer.valueOf(0)) {

        } else if (Integer.valueOf(1) == val) {}
        boolean b = true;
        Boolean.valueOf(b).toString();
    }

    public Integer getBar() {
        return null;
    }

    void doItNow(UnnecessaryBoxing foo) {
        Integer bla = foo == null ? Integer.valueOf(0) : foo.getBar();
    }

    private int i;

    private String s;

    public <T>T get(Class<T> type) {
        if (type == Integer.class) {
            return (T) new Integer(i);
        } else if (type == String.class) {
            return (T) s;
        }
        return null;
    }
}
class IntIntegerTest {
  public IntIntegerTest(Integer val) {
    System.out.println("behavoiur 1");
  }

  public IntIntegerTest(int val) {
    System.out.println("behavoiur 2");
  }

  public static void f(Integer val) {
    System.out.println("behavoiur 1");
  }

  public static void f(int val) {
    System.out.println("behavoiur 2");
  }

  public static void g(int val) {}

  public IntIntegerTest() {
  }

  public void test() {
    new IntIntegerTest(new Integer(1)); // <-- incorrectly triggered
    f(new Integer(1)); // <-- not triggered
    g(((Integer.<warning descr="Unnecessary boxing">valueOf</warning>(1))));
    g(((new <warning descr="Unnecessary boxing">Integer</warning>(1))));
  }

  boolean m(@org.jetbrains.annotations.NotNull Boolean p) {
    Boolean o = null;
    boolean b = o != Boolean.valueOf(false) || p != Boolean.valueOf(false); // object comparison
    return b == Boolean.<warning descr="Unnecessary boxing">valueOf</warning>(false);
  }
}
class test {
  static abstract class AAbstractLongMap extends java.util.AbstractMap<Long, Long> {
    public Long put(long key, Long value) {
      return null;
    }
  }
  static AAbstractLongMap new_times;
  public static void main(String[] args) {
    new_times.put(1l, new Long(2l));
  }
}

class WithLambdaUnfriendlyOverloads {
  interface GetInt { int get(); }
  interface GetInteger { Integer get(); }

  private void m(GetInt getter) {
    System.out.println(getter);
  }

  private void m(GetInteger getter) {
    System.out.println(getter);
  }

  void test(boolean cond) {
    m(() -> {
      if (cond)
        return (new Integer(42));
      else
        return foo();
    });
    m(() -> cond ? new Integer(42) : foo());
    m(() -> new Integer(42));
    m(() -> 42);
  }

  private <T> T foo() {
    return null;
  }

  void testSynchronized() {
    synchronized (Integer.valueOf(123)) {
      System.out.println("hello");
    }
  }

  void testVar() {
    var x = Integer.valueOf(5);
    Integer y = Integer.<warning descr="Unnecessary boxing">valueOf</warning>(5);
    System.out.println(x.getClass());
    System.out.println(y.getClass());
  }

  int testSwitchExpression(int x) {
    return switch(x) {
      default -> Integer.<warning descr="Unnecessary boxing">valueOf</warning>(x);
    };
  }

  public static String foo(int bar) {
    return switch (Integer.valueOf(bar)) {
      case Integer i when i <= 42 -> "42";
      default -> throw new IllegalStateException("Unexpected value: " + bar);
    };
  }
}