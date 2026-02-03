public class m {
    void aa(){}
}

class Outer extends m{
    class I<caret>nner extends Super {
        void bar(){

        }
        void foo() {
            super.foo();
            this.bar();
            Outer.super.aa();
            Outer.this.aa();
        }
    }
}
class Super {
    void foo() {}
}