import java.util.function.*;

public class UnnamedVariables {
  void testParameter(<error descr="Unnamed patterns and variables are not supported at language level '9'">int _</error>, <error descr="Unnamed patterns and variables are not supported at language level '9'">String _</error>) {
    System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>);
  }
  
  <error descr="Unnamed patterns and variables are not supported at language level '9'">int _ = 123;</error>
  String s = <error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>;
  
  void testLambda() {
    Consumer<String> consumer = <error descr="Unnamed patterns and variables are not supported at language level '9'">_</error> -> System.out.println("Hello");
    Consumer<String> consumer2 = <error descr="Unnamed patterns and variables are not supported at language level '9'">_</error> -> System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>);
    Consumer<String> consumer3 = <error descr="Unnamed patterns and variables are not supported at language level '9'">_</error> -> System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>.trim());
    Consumer<String> consumer4 = <error descr="Unnamed patterns and variables are not supported at language level '9'">_</error> -> {
      <error descr="Cannot resolve symbol 'var'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">var</error> v = <error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>;
      System.out.println(v.trim());
    };
    BiConsumer<String, String> consumer5 = (<error descr="Unnamed patterns and variables are not supported at language level '9'">_</error>,<error descr="Unnamed patterns and variables are not supported at language level '9'">_</error>) -> {};
  }
  
  void testLocal() {
    <error descr="Unnamed patterns and variables are not supported at language level '9'">int _ = 10;</error>
    <error descr="Unnamed patterns and variables are not supported at language level '9'">int _ = 20;</error>
    for (<error descr="Unnamed patterns and variables are not supported at language level '9'">int _ = 1;</error>;) {}
  }
  
  void testCatch() {
    try {
      System.out.println();
    }
    catch (<error descr="Unnamed patterns and variables are not supported at language level '9'">Exception _</error>) {
      System.out.println("ignore");
    }
    catch (<error descr="Unnamed patterns and variables are not supported at language level '9'">Error _</error>) {
      <error descr="Unnamed patterns and variables are not supported at language level '9'">int _ = 1;</error>
      for(<error descr="Unnamed patterns and variables are not supported at language level '9'">int _</error>:new int[10]) {
        System.out.println("oops");
      }
    }
  }
}