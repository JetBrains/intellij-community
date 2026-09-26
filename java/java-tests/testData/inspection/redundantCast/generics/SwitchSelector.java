class MyTest {
  public static <T extends Type> String foo(T notificationType, Type t1) {

    if (t1 == null) {
      switch ((Type)notificationType) {
        default:
          return null;
      }
    }
    switch ((<warning descr="Casting 't1' to 'Type' is redundant">Type</warning>)t1) {
      default:
        return null;
    }
  }

  enum Type {
    ;
  }
}