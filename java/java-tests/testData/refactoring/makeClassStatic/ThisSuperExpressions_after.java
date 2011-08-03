public class m {
    void aa(){}
}

class Outer extends m{
    static class Inner extends Super {
        private Outer anObject;

        public Inner(Outer anObject) {
            this.anObject = anObject;
        }

        void bar(){

        }
        void foo() {
            super.foo();
            this.bar();
            anObject.aa();
            anObject.aa();
        }
    }
}
class Super {
    void foo() {}
}