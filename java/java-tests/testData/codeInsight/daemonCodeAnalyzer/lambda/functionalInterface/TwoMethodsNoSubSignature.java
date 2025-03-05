interface X { int m(Iterable<String> arg); }
interface Y { int m(Iterable<Integer> arg); }
<error descr="Multiple non-overriding abstract methods found in Foo">@FunctionalInterface</error>
interface Foo extends X, Y {}
  // Not functional: No method has a subsignature of all abstract methods