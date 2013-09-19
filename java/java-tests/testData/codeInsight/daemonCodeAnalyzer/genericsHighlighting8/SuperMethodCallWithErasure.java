import java.util.Set;

interface Interface {
    void method(Set<?> s);
}

class SuperClass implements Interface {
    public void method(Set s) {
        // do nothing
    }
}

class SubClass extends SuperClass {
    public void method(Set s) {
        super.method(s);  //ERROR: Abstract method 'method(Set<?>)' cannot be accessed directly
    }
}

