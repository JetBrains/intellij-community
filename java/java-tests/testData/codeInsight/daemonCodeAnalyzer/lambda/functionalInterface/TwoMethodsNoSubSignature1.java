interface X { int m(Iterable<String> arg, Class c); }
interface Y { int m(Iterable arg, Class<?> c); }
interface Foo extends X, Y {}
  // Not functional: No method has a subsignature of all abstract methods