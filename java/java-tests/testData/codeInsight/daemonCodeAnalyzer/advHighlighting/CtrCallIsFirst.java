// call to super must be first
public class a {
 a() {}
 a(int i) {}
}

class b extends a {
 b() {
   int i = 3;
   <error descr="Call to 'super()' must be first statement in constructor body">super()</error>;
 }
 b(int i) {
   this();
   <error descr="Call to 'super()' must be first statement in constructor body">super(2)</error>;
 }
 b(char i) {
   super(4);
   <error descr="Call to 'this()' must be first statement in constructor body">this()</error>;
 }

 b(String s) {
   try {
     <error descr="Call to 'super()' must be first statement in constructor body">super(2)</error>;
   }
   finally {
   }
 }
 b(String s, int i) {
   {
     <error descr="Call to 'super()' must be first statement in constructor body">super(2)</error>;
   }
 }

    void f() {
      <error descr="Call to 'super()' must be first statement in constructor body">super()</error>;
    }
    void g() {
      <error descr="Call to 'this()' must be first statement in constructor body">this()</error>;
    }

}
class O extends <error descr="No enclosing instance of type 'A' is in scope">A.B</error>
{
  public O(A a)
  {
      int i = 0;
      <error descr="Call to 'a.super()' must be first statement in constructor body">a.super()</error>;
  }
  public O(A a,int i)
  {
      a.super();
      i = 0;
  }
}

class A
{
  class B
  {
      class C{}
  }

}
