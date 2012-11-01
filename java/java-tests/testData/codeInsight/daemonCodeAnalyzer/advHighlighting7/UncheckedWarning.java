class Test {
  {
    Class foo = Object.class;
    <warning descr="Unchecked call to 'isAssignableFrom(Class<?>)' as a member of raw type 'java.lang.Class'">foo.isAssignableFrom</warning>(Object.class);
  }
}