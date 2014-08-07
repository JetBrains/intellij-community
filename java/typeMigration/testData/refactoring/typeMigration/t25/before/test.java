public class Test {
    C f;
    C foo() {
       return f;
    }
}

class B extends Test {
    C foo() {
        return null;
    }
}

class C {}
class D extends C{}
