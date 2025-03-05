import java.io.*;
import java.util.*;

class UnsupportedFeatures {
  void m(String... args) throws Exception {
    for (String s : args) { System.out.println(s); }

    List<String> list =
      new ArrayList<error descr="Diamond types are not supported at language level '6'"><></error>();

    for (String s : list) {}
    Arrays.asList("");
    Boolean b = true;
    boolean b1 = Boolean.TRUE;

    try { Reader r = new FileReader("/dev/null"); }
    catch (<error descr="Multi-catches are not supported at language level '6'">FileNotFoundException | IOException e</error>) { e.printStackTrace(); }

    try <error descr="Try-with-resources are not supported at language level '6'">(Reader r = new FileReader("/dev/null"))</error> { }

    I i1 = <error descr="Method references are not supported at language level '6'">UnsupportedFeatures::m</error>;
    I i2 = <error descr="Lambda expressions are not supported at language level '6'">() -> { }</error>;

    switch (<error descr="Selector type of 'java.lang.String' is not supported at language level '6'">list.get(0)</error>) {
      case "foo": break;
    }

    switch (list.size()) {
      <error descr="Enhanced 'switch' blocks are not supported at language level '6'">case 0 -> throw new IllegalStateException("empty list");</error>
      default -> System.out.println("it's okay");
    }
    switch (list.size()) {
      case <error descr="Enhanced 'switch' blocks are not supported at language level '6'">1, 2</error>:
    }
    System.out.println(switch (list.size()) {
      <error descr="Enhanced 'switch' blocks are not supported at language level '6'">default -> "whoa!";</error>
    });
  }

  void f(<error descr="Receiver parameters are not supported at language level '6'">Object this</error>) { }

  interface I {
    <error descr="Extension methods are not supported at language level '6'">default void m() { }</error>
    <error descr="Extension methods are not supported at language level '6'">static void m() { }</error>
  }
}