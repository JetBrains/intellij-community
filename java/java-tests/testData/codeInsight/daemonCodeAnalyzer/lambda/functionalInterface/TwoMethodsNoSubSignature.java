interface X { int m(Iterable<String> arg); }
interface Y { int m(Iterable<Integer> arg); }
interface Foo extends X, Y {}
  // Not functional: No method has a subsignature of all abstract methods