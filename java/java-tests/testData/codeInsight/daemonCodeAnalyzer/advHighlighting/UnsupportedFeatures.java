import java.io.*;
import java.util.*;

<error descr="Static imports are not supported at language level '1.4'">import static java.lang.Math.*;</error>

@interface <error descr="Annotations are not supported at language level '1.4'">Anno</error> { }

<error descr="Annotations are not supported at language level '1.4'">@Anno</error>
class UnsupportedFeatures {
  void m(<error descr="Variable arity methods are not supported at language level '1.4'">String... args</error>) throws Exception {
    <error descr="For-each loops are not supported at language level '1.4'">for (String s : args) { System.out.println(s); }</error>
    <error descr="For-each loops are not supported at language level '1.4'">for (Integer i : args) { System.out.println(i); }</error>

    List<error descr="Generics are not supported at language level '1.4'"><String></error> list =
      new ArrayList<error descr="Generics are not supported at language level '1.4'"><></error>();

    <error descr="For-each loops are not supported at language level '1.4'">for (Object s : list) {}</error>
    Arrays.asList<error descr="'asList(java.lang.String...)' in 'java.util.Arrays' cannot be applied to '(java.lang.String)'">("")</error>;
    Boolean b = <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Boolean'">true;</error>
    boolean b1 = Boolean.<error descr="Incompatible types. Found: 'java.lang.Boolean', required: 'boolean'">TRUE</error>;

    java.lang.annotation.ElementType t = null;
    switch (<error descr="Selector type of 'java.lang.annotation.ElementType' is not supported at language level '1.4'">t</error>) { }

    String raw = <error descr="Text block literals are not supported at language level '1.4'">"""hi there"""</error>;

    String spaceEscapeSeq = "<error descr="'\s' escape sequences are not supported at language level '1.4'">\s</error>";
    char c = '<error descr="'\s' escape sequences are not supported at language level '1.4'">\s</error>';

    String template = <error descr="Cannot resolve symbol 'STR'">STR</error>.<error descr="String templates are not supported at language level '1.4'">"Hello \{args[0]}"</error>;
  }
}