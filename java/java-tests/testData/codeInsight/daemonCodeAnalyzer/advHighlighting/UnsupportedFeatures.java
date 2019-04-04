import java.io.*;
import java.util.*;

<error descr="Static imports are not supported at language level '1.4'">import static java.lang.Math.*;</error>

@interface <error descr="Annotations are not supported at language level '1.4'">Anno</error> { }

<error descr="Annotations are not supported at language level '1.4'">@Anno</error>
class UnsupportedFeatures {
  void m(<error descr="Variable arity methods are not supported at language level '1.4'">String... args</error>) throws Exception {
    <error descr="For-each loops are not supported at language level '1.4'">for (String s : args) { System.out.println(s); }</error>

    List<error descr="Generics are not supported at language level '1.4'"><String></error> list =
      new ArrayList<error descr="Generics are not supported at language level '1.4'"><></error>();

    <error descr="For-each loops are not supported at language level '1.4'">for (Object s : list) {}</error>
    Arrays.asList<error descr="'asList(java.lang.String...)' in 'java.util.Arrays' cannot be applied to '(java.lang.String)'">("")</error>;
    <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Boolean'">Boolean b = true;</error>
    <error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'boolean'">boolean b1 = Boolean.TRUE;</error>

    java.lang.annotation.ElementType t = null;
    switch (<error descr="Incompatible types. Found: 'java.lang.annotation.ElementType', required: 'byte, char, short or int'">t</error>) { }

    String raw = <error descr="Raw string literals are not supported at language level '1.4'">`hi there`</error>;
  }
}