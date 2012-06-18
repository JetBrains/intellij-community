// "Create Field for Parameter 'p1'" "true"

class Test{
    private Object myP1;

    <T> void f(T p1){
        myP1 = p1;
    }
}

