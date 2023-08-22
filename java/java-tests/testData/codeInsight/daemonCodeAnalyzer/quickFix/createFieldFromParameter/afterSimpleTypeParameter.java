// "Create field for parameter 'p1'" "true-preview"

class Test{
    private Object myP1;

    <T> void f(T p1){
        myP1 = p1;
    }
}

