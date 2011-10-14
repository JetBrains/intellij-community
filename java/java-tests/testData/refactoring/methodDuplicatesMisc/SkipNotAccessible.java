class B {
    private void <caret>g() {
           f();
       }

       private void f() {
       }
}


class C extends B {
    private void g() {
        f();
    }

    private void f() {
    }
}
