import java.io.*;
import java.util.*;

class UnsupportedFeatures {
  void m(String... args) throws Exception {
    for (String s : args) { System.out.println(s); }

    List<String> list =
      new ArrayList<error descr="Diamond types are not supported at this language level"><></error>();

    for (String s : list) {}
    Arrays.asList("");
    Boolean b = true;
    boolean b1 = Boolean.TRUE;

    try { Reader r = new FileReader("/dev/null"); }
    catch (<error descr="Multi-catches are not supported at this language level">FileNotFoundException | IOException e</error>) { e.printStackTrace(); }

    try <error descr="Try-with-resources are not supported at this language level">(Reader r = new FileReader("/dev/null"))</error> { }

    I i1 = <error descr="Method references are not supported at this language level">UnsupportedFeatures::m</error>;
    I i2 = <error descr="Lambda expressions are not supported at this language level">() -> { }</error>;

    switch (<error descr="Incompatible types. Found: 'java.lang.String', required: 'byte, char, short or int'">list.get(0)</error>) {
      case "foo": break;
    }
  }

  void f(<error descr="Receiver parameters are not supported at this language level">Object this</error>) { }

  interface I {
    <error descr="Extension methods are not supported at this language level">default void m() { }</error>
    <error descr="Extension methods are not supported at this language level">static void m() { }</error>
  }
}
