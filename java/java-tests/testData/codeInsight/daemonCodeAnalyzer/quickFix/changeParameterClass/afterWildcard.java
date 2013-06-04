// "Make 'a' implement 'b'" "true"
interface b<T> {
    void f(T t);
}

class a implements b<Integer> {
    public void f(Integer integer) {
        
    }
}

class X {
    void h(b<? super Integer> i) {

    }

    void g() {
        h(new a());
    }
}