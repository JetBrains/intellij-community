class X {
  void f(Class<? extends Class> aClass) throws ClassNotFoundException {
    Class<String> classOfString = <warning descr="Unchecked assignment: '? extends java.lang.Class' to 'java.lang.Class<java.lang.String>'">aClass.cast</warning>(Long.class);
    System.out.println(classOfString);
  }
}