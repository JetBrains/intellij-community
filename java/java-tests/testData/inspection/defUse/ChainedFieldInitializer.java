import java.util.*;

class Base {
  protected Base(Map<String, Class<?>> aMap) {
  }
}

class SubClass extends Base {
  private static Map<String, Class<?>> aMap = new TreeMap<>();
  static {
    aMap = Collections.unmodifiableMap(aMap);
  }
  public SubClass() { super(aMap); }
}

class WithStatic {
  private static int n = 1;
  static {
    n = n + 1;
  }
}