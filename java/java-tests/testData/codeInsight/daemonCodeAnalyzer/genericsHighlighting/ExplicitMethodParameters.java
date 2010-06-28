import java.util.*;

class Foo {
  <T> void foo() {}
  <T1 extends List, T2> void foo1() {}
  void bar() {}
  <T> void xyz(T l) {}

  {
    foo();
    this.<String>foo();
    this.<error descr="Wrong number of type arguments: 2; required: 1"><String, Integer></error>foo();
    this.<error descr="Method 'bar()' does not have type parameters"><String></error>bar();
    this.<error descr="Method 'bar()' does not have type parameters"><String, Integer></error>bar();
    this.<<error descr="Type parameter 'java.lang.String' is not within its bound; should implement 'java.util.List'">String</error>, Integer>foo1();
    this.<String>xyz<error descr="'xyz(java.lang.String)' in 'Foo' cannot be applied to '(java.lang.Integer)'">(Integer.valueOf("27"))</error>;
    ArrayList list = new <error descr="Method 'ArrayList()' does not have type parameters"><String></error>ArrayList<String>();
  }
}

