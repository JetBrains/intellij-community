import java.util.*;
interface X { Iterable m(Iterable<String> arg); }
interface Y { Iterable<String> m(Iterable arg); }
@FunctionalInterface
interface Foo extends X, Y {}