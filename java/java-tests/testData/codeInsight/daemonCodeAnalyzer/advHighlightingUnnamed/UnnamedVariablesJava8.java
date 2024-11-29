import java.util.function.*;

public class UnnamedVariables {
  void testParameter(int <error descr="Variable '_' is already defined in the scope">_</error>, String <error descr="Variable '_' is already defined in the scope">_</error>) {
    System.out.println(_);
  }
  
  int _ = 123;
  <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">String s = _;</error>
  
  void testLambda() {
    Consumer<String> consumer = <error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> System.out.println("Hello");
    Consumer<String> consumer2 = <error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> System.out.println(_);
    Consumer<String> consumer3 = <error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> System.out.println(_.trim());
    Consumer<String> consumer4 = <error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> {
      <error descr="Cannot resolve symbol 'var'" textAttributesKey="WRONG_REFERENCES_ATTRIBUTES">var</error> v = _;
      System.out.println(v.trim());
    };
    BiConsumer<String, String> consumer5 = (<error descr="Use of '_' as a lambda parameter name is not allowed"><error descr="Variable '_' is already defined in the scope">_</error></error>,<error descr="Use of '_' as a lambda parameter name is not allowed"><error descr="Variable '_' is already defined in the scope">_</error></error>) -> {};
  }
  
  void testLocal() {
    int _ = 10;
    int <error descr="Variable '_' is already defined in the scope">_</error> = 20;
    for (int <error descr="Variable '_' is already defined in the scope">_</error> = 1;;) {}
  }
  
  void testCatch() {
    try {
      System.out.println();
    }
    catch (Exception _) {
      System.out.println("ignore");
    }
    catch (Error _) {
      int <error descr="Variable '_' is already defined in the scope">_</error> = 1;
      for(int <error descr="Variable '_' is already defined in the scope">_</error>:new int[10]) {
        System.out.println("oops");
      }
    }
  }
}