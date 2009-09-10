class Base {
    void foo() {
    }

    class Inner {
        void bar() {
            Base.this.foo();
        }
    }
}

public class QualifiedThis extends Base {
    void foo() {
    }

}