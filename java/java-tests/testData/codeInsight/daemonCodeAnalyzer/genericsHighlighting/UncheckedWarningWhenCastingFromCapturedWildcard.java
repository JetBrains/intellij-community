class X {
  void f(Class<? extends Class> aClass) throws ClassNotFoundException {
    Class<String> classOfString = <warning descr="Unchecked assignment: 'capture<? extends java.lang.Class>' to 'java.lang.Class<java.lang.String>'">aClass.cast(Long.class)</warning>;
    System.out.println(classOfString);
  }
}