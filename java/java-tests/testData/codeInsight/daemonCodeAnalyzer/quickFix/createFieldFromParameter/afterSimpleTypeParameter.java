// "Create Field For Parameter 'p1'" "true"

class Test{
    private final Object myP1;

    <T> void f(T p1){
        myP1 = p1;
    }
}

