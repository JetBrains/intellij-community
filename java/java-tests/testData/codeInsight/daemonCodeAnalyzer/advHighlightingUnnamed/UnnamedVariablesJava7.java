import java.util.function.*;

public class UnnamedVariables {
  void testParameter(int <error descr="Variable '_' is already defined in the scope">_</error>, String <error descr="Variable '_' is already defined in the scope">_</error>) {
    System.out.println(_);
  }
  
  void testOneParam(String _) {
    System.out.println(_);
  }
  
  int _ = 123;
  String s = <error descr="Incompatible types. Found: 'int', required: 'java.lang.String'">_</error>;
  
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