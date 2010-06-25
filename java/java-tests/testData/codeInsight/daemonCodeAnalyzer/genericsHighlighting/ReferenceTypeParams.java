import java.util.*;

class C<T,U> {

  C c1 = new C<error descr="Wrong number of type arguments: 1; required: 2"><Integer></error>();
  C c2 = new C<error descr="Wrong number of type arguments: 3; required: 2"><Integer, Float, Object></error>();
  Object o = new Object<error descr="Type 'java.lang.Object' does not have type parameters"><C></error>();

  C c3 = new C();
  C c4 = new C<Object, C>();
  C<Integer, Float> c5 = new C<Integer, Float>(); 
}

class D<T extends C> {
  {
    new D<<error descr="Type parameter 'java.lang.Integer' is not within its bound; should extend 'C'">Integer</error>>();
    new D<C>();
    class CC extends C {};
    new D<CC>();
    new D<T>();
  }

  T field = new <error descr="Type parameter 'T' cannot be instantiated directly">T</error>();
  T field2 = new <error descr="Type parameter 'T' cannot be instantiated directly">T</error>() { };
  T[] array = new <error descr="Type parameter 'T' cannot be instantiated directly">T</error>[10];
}

class Primitives<T> {
  Object a = new Primitives<<error descr="Type argument cannot be of primitive type">? extends int</error>>();
  Object o = new Primitives<<error descr="Type argument cannot be of primitive type">int</error>>();
  void f(Primitives<<error descr="Type argument cannot be of primitive type">boolean</error>> param) {
    if (this instanceof Primitives<<error descr="Type argument cannot be of primitive type">double</error>>) {
      return;
    }
  }
}


/////// calling super on generic bound class
public class Generic<T> {
    Generic(T t){}
}
public class Bound extends Generic<String>{
    public Bound(String s) {
        super(s);
    }
}

////
class Generic2<T1,T2> {
  class A {}
  class B {}
  private <error descr="Incompatible types. Found: 'Generic2<java.lang.String,Generic2.B>', required: 'Generic2<java.lang.String,Generic2.A>'">Generic2<String, A> map = new Generic2<String,B>();</error>
  {
    <error descr="Incompatible types. Found: 'Generic2<java.lang.String,java.lang.String>', required: 'Generic2<java.lang.String,Generic2.A>'">map = new Generic2<String,String>()</error>;
    map = new Generic2<String,A>();
  }
}

class DummyList<T> {}
abstract class GenericTest3 implements DummyList<<error descr="No wildcard expected">? extends String</error>> {
    DummyList<DummyList<? extends DummyList>> l;
    <T> void foo () {}
    void bar () {
         this.<DummyList<? extends DummyList>>foo();
         DummyList<DummyList<? super String>>[] l = <error descr="Generic array creation">new DummyList<DummyList<? super String>>[0]</error>;
         DummyList<String>[] l1 = <error descr="Generic array creation">{}</error>;
    }

    public <T> T[] getComponents (Class<T> baseInterfaceClass) {
        T[] ts = <error descr="Generic array creation">{}</error>;

        return ts;
    }
}

class mylist<T> {}
class myAList<T> extends mylist<T> {
  {
        mylist<String> l = <error descr="Inconvertible types; cannot cast 'myAList<java.lang.Integer>' to 'mylist<java.lang.String>'">(mylist<String>) new myAList<Integer>()</error>;
        boolean b = <error descr="Operator '==' cannot be applied to 'myAList<java.lang.Integer>','myAList<java.lang.String>'">new myAList<Integer>() == new myAList<String>()</error>;

        if (l instanceof <error descr="Illegal generic type for instanceof">myAList<String></error>);
        Object o = new Object();
        if (o instanceof <error descr="Class or array expected">T</error>);
  }

  Class<T> foo (Class<T> clazz) {
        Class<String> clazz1 = (Class<String>)clazz;  //Should be unchecked warning
        return <error descr="Cannot select from a type variable">T</error>.class;
  }
}

class testDup<T, <error descr="Duplicate type parameter: 'T'">T</error>> { // CAN IT BE HIGHLIGHTED? b
    public <T, <error descr="Duplicate type parameter: 'T'">T</error>> void foo() { // CAN IT BE HIGHLIGHTED?
    }
}

class aaaa {
    {
        <error descr="Incompatible types. Found: 'java.lang.Class<java.lang.String>', required: 'java.lang.Class<? super java.lang.Object>'">Class<? super Object> c = String.class;</error>
    }
}

//IDEADEV-6103: this code is OK
class Foo {
    mylist<Test> foo;

    public Foo(mylist<Test> foo) {
        this.foo = foo;
    }

    public Foo() {
          this(new mylist<Test>());
    }

    private class Test {
    }
}
//end of IDEADEV-6103

class IDontCompile {
   Map<error descr="Cannot select static class 'java.util.Map.Entry' from parameterized type"><?, ?></error>.Entry map;
}

abstract class GenericTest99<E extends Enum<E>> {
    GenericTest99<<error descr="Type parameter 'java.lang.Enum' is not within its bound; should extend 'java.lang.Enum<java.lang.Enum>'">Enum</error>> local;
}