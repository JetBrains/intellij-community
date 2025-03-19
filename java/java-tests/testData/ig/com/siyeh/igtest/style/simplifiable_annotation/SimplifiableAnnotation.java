package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    @<warning descr="Unnecessary whitespace in annotation"> </warning>SuppressWarnings<warning descr="Unnecessary whitespace in annotation">  </warning>(<warning descr="Unnecessary 'value =' in annotation">value = </warning>"blabla")
    @<warning descr="Unnecessary whitespace in annotation"> </warning>Deprecated<warning descr="Unnecessary '()' in annotation">()</warning>
    Object foo() {
        return null;
    }
}
@interface ValueAnnotation {
  String[] value();
}
@interface ArrayAnnotation {
  String[] array();
}
@ValueAnnotation(<warning descr="Unnecessary braces around '{\"the value\"}' in annotation">{</warning>"the value"<warning descr="Unnecessary braces around '{\"the value\"}' in annotation">}</warning>)
@ArrayAnnotation(array = <warning descr="Unnecessary braces around '{\"first\"}' in annotation">{</warning>"first"<warning descr="Unnecessary braces around '{\"first\"}' in annotation">}</warning>)
class MyClass {

  @ <error descr="'value' missing but required">ValueAnnotation</error>
  int foo(@ArrayAnnotation(array="") String s) {
    return -1;
  }

  @Two(i=<warning descr="Unnecessary braces around '{1}' in annotation">{</warning>1<warning descr="Unnecessary braces around '{1}' in annotation">}</warning>, j = 2)
  String s;
}
@interface Two {
  int[] i();
  int j();
}
