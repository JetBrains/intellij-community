enum SomeEnumeration {
  A;

  public static SomeEnumeration fromValue(String v) {
    return  valueOf(v);
  }
}


class IllegalArgumentException extends Exception {}
