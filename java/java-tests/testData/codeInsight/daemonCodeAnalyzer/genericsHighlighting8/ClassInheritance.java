class D implements I<I>{
}
class DT<T> implements I<T>{
}

interface I <T> {
}

<error descr="'I' cannot be inherited with different type arguments: 'I' and 'D'">class CCC extends D implements I<D></error> {
}
abstract class CCC2<T> extends DT<T> implements I<T> {
}

class a extends b<d, c> implements c<d, c<c,c>>, d<b<c<c,c>,d>> {}
public class b<K,V> implements c<K, c<V,V>> { }
interface c<K,V> extends d<b<V,K>> {}
interface d<K> {}

// extending final classes in bounds
class C<T extends String> {
 <E extends Integer> void f() {}
}

class GenericExtendItself<T, U extends T>
{
    GenericExtendItself<Object,Object> foo;
}


////////////////////
public abstract class ZZZZ<E> {
    public abstract E getElement();
}
abstract class Z<E> extends ZZZZ<E> {}
class Z2 extends Z<Integer> {
    public Integer getElement() {
        return null;  
    }
}
/////////////////
class BaseC <E> {
    E remove(){
        return null;
    }
}

class DerivedC extends BaseC<String> {
    public String remove() {
        String s = super.remove();
        return null;
    }
}

/// raw in the multiple supers
interface Int<T> {
    AClass<T> f();
}
abstract class AClass<T> implements Int<T> {
    public abstract AClass<T> f();
}

class MyClass extends AClass implements Int{
    public AClass f() {
        return null;
    }
}

class A<T>{
  A(){}
  A(T t){}

  {
   new A<A>(new A()){};
  }
}

//IDEADEV-4733: this overriding is OK
class Outer<T>
{
    public class Inner
    {
        private final T t;

        public Inner(T t) { this.t = t; }

        public T getT() { return t; }

        public String toString() { return t.toString(); }
    }
}

class Other extends Outer<String>
{
    public class Ither extends Outer<String>.Inner
    {
        public Ither()
        {
            super("hello"); //valid super constructor call
        }
    }
}

//end of //IDEADEV-4733
interface AI {
}
interface BI {
}
abstract class AbstractClass<T> {
    AbstractClass(Class<T> clazz) {
    }
}
class ConcreteClass extends AbstractClass<AI> {
    ConcreteClass() {
        super(AI.class);
    }
    class InnerClass extends AbstractClass<BI> {
        InnerClass() {
            super(BI.class); //
        }
    }
}
///////////////////////
