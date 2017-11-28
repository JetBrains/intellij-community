class A {}
class B {}

public class Test {
    A getA() {
        return new A ();
    }

    int foo() {
        A a = getA ();

        if (a != null){
            return 0;
        }

        return 1;
    }
}
