enum SomeEnumeration {
  A;

  public static SomeEnumeration fromValue(String v) {
    return  valueOf(v);
  }
}


public class IllegalArgumentException extends Exception {}
