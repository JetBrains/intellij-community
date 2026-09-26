import java.util.*;
interface X { int m(Iterable<String> arg); }
interface Y { int m(Iterable<String> arg); }
@FunctionalInterface
interface Foo extends X, Y {}