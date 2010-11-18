// "Create Method 'f'" "true"
class A {
    <T> T foo(){
       B<T> x = f();
    }

    private <T> B<T> f() {
        <selection>return null;  //To change body of created methods use File | Settings | File Templates.</selection>
    }
}

class B<K>{}
