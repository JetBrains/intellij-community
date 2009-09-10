class A {
    class <caret>Inner {
        private void a() {
        }

        private void b() {
        }

        class InnerA {
        }

        class InnerB {
        }
    }
}

class B {
    public void test() {
        A.Inner inner = new A.Inner();
    }
}