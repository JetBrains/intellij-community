// "Create Method 'get'" "true"
class W<T> {
    public T get(T s) {
        <selection>return null;  //To change body of created methods use File | Settings | File Templates.</selection>
    }
}

class C {
    void foo () {
        W<String> w = new W<String>();
        String s = w.get("");
    }
}