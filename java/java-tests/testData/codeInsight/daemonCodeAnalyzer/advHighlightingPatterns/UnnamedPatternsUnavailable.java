public class UnnamedPatterns {
  record R(int a, int b) {}
  
  void test(Object obj) {
    if (obj instanceof <error descr="Since Java 9, '_' is a keyword and may not be used as an identifier">_</error>) {}
    
    if (obj instanceof R(<error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>, <error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>)) {}
    if (obj instanceof R(int a, <error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>)) {
      System.out.println(a);
    }
    if (obj instanceof R(<error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>, int b)) {
      System.out.println(b);
    }
    if (obj instanceof R(<error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>, <error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>, <error descr="Unnamed patterns and variables are not supported at language level '20'">_</error>)) {
    }
  }
}