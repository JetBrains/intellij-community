// "Create Field For Parameter 'p1'" "true"

class Test<T>{
    private final T myP1;

    void f(T p1){
        myP1 = p1;
    }
}

