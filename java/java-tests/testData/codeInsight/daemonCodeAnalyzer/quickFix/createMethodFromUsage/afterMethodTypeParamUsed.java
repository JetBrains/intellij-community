// "Create method 'f'" "true-preview"
class A {
    <T> T foo(){
       B<T> x = f();
    }

    private <T> B<T> f() {
        <selection>return null;</selection>
    }
}

class B<K>{}
