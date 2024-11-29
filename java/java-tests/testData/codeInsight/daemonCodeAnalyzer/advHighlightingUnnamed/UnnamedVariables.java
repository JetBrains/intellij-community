import java.util.function.*;

public class UnnamedVariables {
  void testParameter(<error descr="Unnamed method parameter is not allowed">int _</error>, <error descr="Unnamed method parameter is not allowed">String _</error>) {
    System.out.println(<error descr="Using '_' as a reference is not allowed">_</error>);
  }
  
  <error descr="Unnamed field is not allowed">int _</error> = 123;
  String s = <error descr="Using '_' as a reference is not allowed">_</error>;
  
  void testLambda() {
    Consumer<String> consumer = _ -> System.out.println("Hello");
    Consumer<String> consumer2 = _ -> System.out.println(<error descr="Using '_' as a reference is not allowed">_</error>);
    Consumer<String> consumer3 = _ -> System.out.println(<error descr="Using '_' as a reference is not allowed">_</error>.trim());
    Consumer<String> consumer4 = _ -> {
      var v = <error descr="Using '_' as a reference is not allowed">_</error>;
      System.out.println(v.trim());
    };
    BiConsumer<String, String> consumer5 = (_,_) -> {};
  }
  
  void testWhen(Object obj) {
    switch (obj) {
      case String _ when <error descr="Using '_' as a reference is not allowed">_</error>.isEmpty() -> {}
    }
  }
  
  void testLocal() {
    int _ = 10;
    int _ = 20;
    int _<error descr="Brackets are not allowed after an unnamed variable declaration">[]</error> = {30};
    int[] _ = {40};
    var _ = "string";
    for (int _ = 1;;) {}
  }
  
  void testNoInitializer() {
    <error descr="Unnamed variable declaration must have an initializer">int _</error>;
    
    for(<error descr="Unnamed variable declaration must have an initializer">int _</error>;;) {}
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