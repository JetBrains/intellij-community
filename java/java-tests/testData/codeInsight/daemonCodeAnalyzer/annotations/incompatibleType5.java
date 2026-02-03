@interface Ann {
    Class type();
}

class C {
  private static final Class<C> THIS_TYPE = C.class;

  @Ann(type = <error descr="Attribute value must be a class literal">THIS_TYPE</error>)
  void bad() { }

  @Ann(type = C.class)
  void good() { }
}
