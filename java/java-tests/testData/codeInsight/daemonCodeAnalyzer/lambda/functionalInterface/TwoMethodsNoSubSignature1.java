interface X { int m(Iterable<String> arg, Class c); }
interface Y { int m(Iterable arg, Class<?> c); }
<error descr="Multiple non-overriding abstract methods found in Foo">@FunctionalInterface</error>
interface Foo extends X, Y {}
  // Not functional: No method has a subsignature of all abstract methods