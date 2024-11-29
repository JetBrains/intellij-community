import java.util.Map;
import java.util.Set;
import java.io.IOException;
import java.util.function.IntFunction;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">Anno</info>;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">MyInterface</info>;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">pkg</info>.<info descr="Not resolved until the project is fully loaded">Cls</info>;
<warning descr="Unused import statement">import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">UnusedClass</info>;</warning>
<warning descr="Unused import statement">import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">Value</info>;</warning>
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">UsedInClassObject</info>;
import <info descr="Not resolved until the project is fully loaded">my</info>.<info descr="Not resolved until the project is fully loaded">unknown</info>.<info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>;

public class Simple {
  int test() {
    test().<error descr="Cannot call methods on 'int' type">run</error>();
    return 0;
  }

  void refsInResolvedClass(String s) {
    System.out.println(String.<error descr="Cannot resolve symbol 'STATIC'">STATIC</error>);
    s.trim();
    s.<error descr="Cannot resolve method 'dream' in 'String'">dream</error>();
    System.out.println(s.<error descr="Cannot resolve symbol 'field'">field</error>);
  }
  
  void callKnownCtor(<info descr="Not resolved until the project is fully loaded">Cls</info> cls) {
    new IOException(cls);
    // No three-arg ctor anyway
    new IOException<error descr="Cannot resolve constructor 'IOException(Cls, Cls, Cls)'">(cls, cls, cls)</error>;
  }
  
  void testImports(<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">UnusedClass</info> inner) {
    <info descr="null">var</info> x = <error descr="Cannot resolve symbol 'Value'">Value</error>;
    System.out.println(<info descr="Not resolved until the project is fully loaded">UsedInClassObject</info>.class);
    Runnable r = <info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>::<info descr="Not resolved until the project is fully loaded">foo</info>;
  }
  
  void testMethodRef() {
    Runnable r1 = <info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>::new;
    <error descr="Incompatible types. Found: '<method reference>', required: 'java.lang.Runnable'">Runnable r2 = <info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>[]::new;</error>
    IntFunction<<info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>[]> r3 = <info descr="Not resolved until the project is fully loaded">UsedInMethodRef</info>[]::new;
    Runnable r4 = String::<error descr="Cannot resolve method 'blahblah'">blahblah</error>;
    Runnable r5 = <error descr="Non-static method cannot be referenced from a static context">String::getBytes</error>;
    Runnable r6 = "hello"::trim;
  }

  void refsInUnresolvedClass(<info descr="Not resolved until the project is fully loaded">Cls</info> s) {
    s.hashCode();
    s.dream();
    System.out.println(s.field);
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
  
  void testEquality(<info descr="Not resolved until the project is fully loaded">Unknown</info> u, <info descr="Not resolved until the project is fully loaded">Unknown2</info> u2,
                    char c, boolean b) {
    if (u == u2) {}
    if (u != u2) {}
    if (<error descr="Operator '==' cannot be applied to 'char', 'boolean'">c == b</error>) {}
  }
  
  @<info descr="Not resolved until the project is fully loaded">Anno</info>(<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">CONST</info>)
  void testAssign(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    u.field = 2;
  }
  
  void callOnArray(<info descr="Not resolved until the project is fully loaded">Unknown</info> u) {
    u.foo()[0].<info descr="Not resolved until the project is fully loaded">blah</info>();
  }
  
  void initArray() {
    <info descr="Not resolved until the project is fully loaded">Cls</info>[] array = {<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">createCls</info>()};
  }
  
  void callOverloaded(Set<<info descr="Not resolved until the project is fully loaded">Cls</info>> mySet) {
    overloaded(<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">getValue</info>());
    mySet.toArray(<info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">EMPTY_ARRAY</info>);
  }
  
  void varTest() {
    <info descr="null">var</info> x = <info descr="Not resolved until the project is fully loaded">Cls</info>.<info descr="Not resolved until the project is fully loaded">getSomething</info>();
    x.getSomethingElse();
    <info descr="null">var</info> y = x;
    <info descr="null">var</info> z = y.getSomethingCompletelyDifferent();
    z.getFromZ();
    
    <info descr="null">var</info> t = <error descr="Variable 't' might not have been initialized">t</error>;
  }
  
  void overloaded(int x) {}
  void overloaded(boolean x) {}
  
  void useInner() {
    Clss.Inner cls = new Clss.Inner();
    Clss.Inner[] result = (Clss.Inner[]) cls.<info descr="Not resolved until the project is fully loaded">toArray</info>(12);
  }


  void testThrow(<info descr="Not resolved until the project is fully loaded">Cls</info> cls) {
    try {
      cls.unknownM();
    } catch (<info descr="Not resolved until the project is fully loaded">Cls</info> x) {

    } catch (IOException | RuntimeException ex) {

    }
  }
  
  void testThrow2() {
    try {
      declaredUnknownException();
    }
    catch (<info descr="Not resolved until the project is fully loaded">Cls</info> x) {}
  }
  
  void testThrow3() {
    // We don't know whether Cls is checked or not
    declaredUnknownException();
  }
  
  void declaredUnknownException() throws <info descr="Not resolved until the project is fully loaded">Cls</info> {}
  
  void testConcat(<info descr="Not resolved until the project is fully loaded">Cls</info> cls) {
    System.out.println("hello " + cls.getSomething() + "!!!");
  }
  
  static class Clss implements <info descr="Not resolved until the project is fully loaded">MyInterface</info> {
    void run() {
      <info descr="Not resolved until the project is fully loaded">foo</info>(<info descr="Not resolved until the project is fully loaded">bar</info>);
    }
    
    static class Inner extends <info descr="Not resolved until the project is fully loaded">Cls</info> {
      
    }
  }
}