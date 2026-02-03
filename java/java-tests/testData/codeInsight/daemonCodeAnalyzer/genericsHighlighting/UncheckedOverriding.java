class List<T> { T t;}

class Base<T> {
    List<T> getList(List<T> l) {
        return null;
    }


}

class Derived extends Base <String> {
    <warning descr="Unchecked overriding: return type requires unchecked conversion. Found 'List', required 'List<java.lang.String>'">List</warning> getList(List<String> l) {
        return null;
    }
}

class A1 {
    <T> T foo(T t) {
        return null;
    }
}

class A2 extends A1 {
    <warning descr="Unchecked overriding: return type requires unchecked conversion. Found 'java.lang.Object', required 'T'">Object</warning> foo(Object o) {
        return null;
    }
}

//IDEADEV-15918
abstract class Outer<U> {
    public abstract Inner m(U u);

    public class Inner {
    }
}

class Other extends Outer<Other> {
    public Ither m(Other other) {
        return new Ither();
    }

    public class Ither extends Inner {
    }
}
//end of IDEADEV-15918