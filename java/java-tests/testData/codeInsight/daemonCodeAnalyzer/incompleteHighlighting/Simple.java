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
  
  void methodCall(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    method("Hello", u);
    method(<error descr="'method(java.lang.String, java.lang.Object)' in 'Simple' cannot be applied to '(Unknown, java.lang.String)'">u</error>, "Hello");
    method(<error descr="'method(java.lang.String, java.lang.Object)' in 'Simple' cannot be applied to '(Unknown, Unknown)'">u</error>, u);
  }
}