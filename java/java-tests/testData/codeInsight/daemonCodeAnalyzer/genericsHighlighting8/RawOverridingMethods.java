import java.util.*;

class Base<T> {
    public void method(Base<?> base) { }
    public void method1(Base<Base<?>> base) { }
    public <V> Base<V> foo() { return null; }
    public Base<?> bar() { return null; }
    public Base<Base<?>> far() { return null; }
}

class Derived extends Base {
    public void method(Base base) { }
    public Base foo() { return null; }
    public Base bar() { return null; }
}

class Derived1 extends Base {
    <error descr="'method1(Base<String>)' in 'Derived1' clashes with 'method1(Base<Base<?>>)' in 'Base'; both methods have same erasure, yet neither overrides the other">public void method1(Base<String> base)</error> { }
    public Base<String> far() { return null; } // Acceptable construct as of JDK 1.5 beta 2 may 2004
}

class X <T> {
    public <V> void foo () {}
}

class YY extends X {
    <error descr="'foo()' in 'YY' clashes with 'foo()' in 'X'; both methods have same erasure, yet neither overrides the other">public <V> void foo()</error> {}
}

interface List<Y> {
    public <T> T[] toArray(T[] ts);
}
class AbstractList<Y> {
    public <T> T[] toArray(T[] ts) {return null;}
}
//Signatures from List and AbstractList are equal
class ArrayList extends AbstractList implements List {}

//SCR 39485: the following overriding is OK
abstract class Doer {
    abstract <X> void go(X x);
}

class MyList <X>
   extends Doer {
   X x; 
   <Y> void go(Y y) {}
}

class MyListRaw
       extends MyList {
}

//See IDEADEV-1125
//The following two classes are OK
class A1 {
    <T> void foo(T t) {}
}

class A2 extends A1 {
    void foo(Object o) {}
}

//While these are not
class A3 {
    void foo(Object o) {}
}

class A4 extends A3 {
    <error descr="'foo(T)' in 'A4' clashes with 'foo(Object)' in 'A3'; both methods have same erasure, yet neither overrides the other"><T> void foo(T t)</error> {}
}

//This sibling override is OK
class A5 {
    public void foo(Object o) {}
}

interface I1 {
    <T> void foo(T t);
}

class A6 extends A5 implements I1 {}

//While this is not
class A7 {
    public <T> void foo(T t) {}
}

interface I2 {
    public void foo(Object o);
}

<error descr="Class 'A8' must either be declared abstract or implement abstract method 'foo(Object)' in 'I2'">class A8 extends A7 implements I2</error> {}

//IDEA-9321
abstract class MyMap<K, V> implements java.util.Map<K, V> {
    public  <error descr="'put(K, V)' in 'MyMap' clashes with 'put(K, V)' in 'java.util.Map'; attempting to use incompatible return type">Object</error> put(K key, V value) {
        return null;
    }
}
//end of IDEA-9321

abstract class AA <T> {
    abstract void foo(T t);
}

abstract class BB<T> extends AA<BB> {
    void foo(BB b) {}
}

class CC extends BB {
  //foo is correctly seen from BB
}

class QQQ {}

abstract class GrandParent<T> {
    public abstract void paint(T object);
}

class Parent<T extends QQQ> extends GrandParent<T> {
    public void paint(T component) {
    }
}

// this overriding should be OK
class Child2 extends Parent {

}

class IDEA16494  {
    class Base<B> {
        public List<B> elements() {
            return null;
        }
    }

    class Derived<T> extends Base<T[]> {
    }

    class MostDerived extends Derived {

        public List<MostDerived[]> elements() {
            return null;
        }
    }
}
class IDEA16494Original  {
    class Base<B> {
        public List<B> elements() {
            return null;
        }
    }

    class Derived<T> extends Base<T> {
    }

    class MostDerived extends Derived {

        public List<MostDerived> elements() {
            return null;
        }
    }
}
class IDEADEV23176Example {
  public abstract class AbstractBase<E> extends AbstractParent<E> implements Interface<E> {
  }
  public abstract class AbstractParent<E> {
    public void Implemented(Collection<?> c) {
    }
    public abstract void mustImplement();
  }
  public class Baseclass extends AbstractBase implements Interface {
    public void mustImplement() {
    }
  }
  public interface Interface<E> {
    void Implemented(Collection<?> c);
  }
}

/** @noinspection UnusedDeclaration*/
class IDEADEV26185
{
    public static abstract class SuperAbstract<Owner, Type>
    {
        public abstract Object foo(Type other);
    }

    public static abstract class HalfGenericSuper<Owner> extends SuperAbstract<Owner, String>
    {
        public abstract Object foo(String other);
    }

    public static abstract class AbstractImpl<Owner> extends HalfGenericSuper<Owner>
    {
        public Object foo(String other)
        {
            return null;
        }
    }

    public static class Concrete extends AbstractImpl
    {
    }
}

class ideadev30090 {
  abstract class MyBeanContext
        implements MyListInterface/*<MyListMember>*/ {
    public Object get(int index) {
        return null;
    }
  }

  interface MyListInterface<E extends MyListMember>
        extends List<E> {
  }
  interface MyListMember {
     void f();
  }
}
//////////////////////////////////////////
class IDEADEV32421 {
 interface InterfaceWithFoo {
    Class<?> foo();
 }

 class ParentWithFoo implements InterfaceWithFoo {
    public Class foo() {
        return null;
    }
 }

 class TestII extends ParentWithFoo implements InterfaceWithFoo {
 }
}

class IDEADEV32421_TheOtherWay {
 interface InterfaceWithFoo {
    Class foo();
 }

 class ParentWithFoo implements InterfaceWithFoo {
    public Class<?> foo() {
        return null;
    }
 }

 class TestII extends ParentWithFoo implements InterfaceWithFoo {
 }
}
//////////////////////////////////////
class SBBug {
  abstract class A<T> implements Comparable<A<T>> {}

  class B extends A {
      public int compareTo(Object o) {
          return 0;
      }
  }
}
class SBBug2 {
    abstract class A<T> implements Comparable<A<T>> {}

    <error descr="Class 'B' must either be declared abstract or implement abstract method 'compareTo(T)' in 'Comparable'">class B extends A</error> {
        public int compareTo(A o) {
            return 0; 
        }
    }
}
