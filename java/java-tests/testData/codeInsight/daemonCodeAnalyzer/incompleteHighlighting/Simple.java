import java.util.Map;
import java.io.IOException;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">Anno</info>;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">MyInterface</info>;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">Cls</info>;

public class Simple {
  int test() {
    test().<error descr="Cannot call method because 'test()' has primitive type int">run</error>();
    return 0;
  }

  void refsInResolvedClass(String s) {
    System.out.println(String.<error descr="Cannot resolve symbol 'STATIC'">STATIC</error>);
    s.trim();
    s.<error descr="Cannot resolve method 'dream' in 'String'">dream</error>();
    System.out.println(s.<error descr="Cannot resolve symbol 'field'">field</error>);
  }

  void refsInUnresolvedClass(<info descr="Not resolved until the project is fully loaded">Cls</info> s) {
    s.<info descr="Not resolved until the project is fully loaded">hashCode</info>();
    s.<info descr="Not resolved until the project is fully loaded">dream</info>();
    System.out.println(s.<info descr="Not resolved until the project is fully loaded">field</info>);
    System.out.println(<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">STATIC</info>);
  }

  void assign(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    <error descr="Incompatible types. Found: 'Unknown', required: 'java.lang.String'">String s = u;</error>
    Number n = u;
    <info descr="Not resolved until the project is fully loaded">Unknown2</info> u2 = u;
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'Unknown2'"><info descr="Not resolved until the project is fully loaded">Unknown2</info> u3 = s;</error>
  }
  
  void knownTypes(String s) {
    <error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.Boolean'">Boolean b = s;</error>
    
  }
  
  void method(String s, Object obj) {}
  
  private void methodThrows() throws <warning descr="Exception 'java.io.IOException' is never thrown in the method">IOException</warning> {
    System.out.println();
  }
  
  void methodCall(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    method("Hello", u);
    method(<error descr="'method(java.lang.String, java.lang.Object)' in 'Simple' cannot be applied to '(Unknown, java.lang.String)'">u</error>, "Hello");
    method(<error descr="'method(java.lang.String, java.lang.Object)' in 'Simple' cannot be applied to '(Unknown, Unknown)'">u</error>, u);
  }
  
  @<info descr="Not resolved until the project is fully loaded">Anno</info>
  void annotated(Map<String, @<info descr="Not resolved until the project is fully loaded">Anno</info> String> map) {}
  
  void cast(String s, <info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    <info descr="Not resolved until the project is fully loaded">Unknown2</info> u2 = (<info descr="Not resolved until the project is fully loaded">Unknown</info>)u;
    String s2 = <error descr="Inconvertible types; cannot cast 'Unknown' to 'java.lang.String'">(String)u</error>;
  }
  
  void instanceOf(String s, <info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    if (u instanceof <info descr="Not resolved until the project is fully loaded">Unknown2</info>) {}
    if (<error descr="Inconvertible types; cannot cast 'Unknown' to 'java.lang.String'">u instanceof String</error>) {}
    if (<error descr="Inconvertible types; cannot cast 'java.lang.String' to 'Unknown'">s instanceof <info descr="Not resolved until the project is fully loaded">Unknown</info></error>) {}
  }
  
  void callOnArray(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    u.<info descr="Not resolved until the project is fully loaded">foo</info>()[0].<info descr="Not resolved until the project is fully loaded">blah</info>();
  }
  
  static class Clss implements <info descr="Not resolved until the project is fully loaded">MyInterface</info> {
    void run() {
      <info descr="Not resolved until the project is fully loaded">foo</info>(<info descr="Not resolved until the project is fully loaded">bar</info>);
    }
  }
}