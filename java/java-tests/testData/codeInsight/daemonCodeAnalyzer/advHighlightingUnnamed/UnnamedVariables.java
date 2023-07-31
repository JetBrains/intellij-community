import java.util.function.*;

public class UnnamedVariables {
  void testParameter(int <error descr="Unnamed method parameter is not allowed">_</error>, String <error descr="Unnamed method parameter is not allowed">_</error>) {
    System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>);
  }
  
  int <error descr="Unnamed field is not allowed">_</error> = 123;
  String s = <error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>;
  
  void testLambda() {
    Consumer<String> consumer = _ -> System.out.println("Hello");
    Consumer<String> consumer2 = _ -> System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>);
    Consumer<String> consumer3 = _ -> System.out.println(<error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>.trim());
    Consumer<String> consumer4 = _ -> {
      var v = <error descr="Since Java 9, '_' is a keyword, and may not be used as an identifier">_</error>;
      System.out.println(v.<error descr="Cannot resolve method 'trim()'">trim</error>());
    };
    BiConsumer<String, String> consumer5 = (_,_) -> {};
  }
  
  void testLocal() {
    int _ = 10;
    int _ = 20;
    for (int <error descr="Unnamed local variable is not allowed in this context">_</error> = 1;;) {}
  }
  
  void testCatch() {
    try {
      System.out.println();
    }
    catch (Exception _) {
      System.out.println("ignore");
    }
    catch (Error _) {
      int _ = 1;
      for(int _:new int[10]) {
        System.out.println("oops");
      }
    }
  }
}