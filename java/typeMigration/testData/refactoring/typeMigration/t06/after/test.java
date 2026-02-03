class A {}
class B {}

public class Test {
    B getA() {
        return new A ();
    }

    int foo() {
        B a = getA ();

        if (a != null){
            return 0;
        }

        return 1;
    }
}
