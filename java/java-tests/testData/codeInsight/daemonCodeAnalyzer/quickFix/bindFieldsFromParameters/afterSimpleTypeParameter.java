// "Bind method parameters to fields" "true-preview"

class A{
    private Object myP1;
    private Object myP2;

    <T> void f(T p1, T p2){
        myP1 = p1;
        myP2 = p2;
    }
}

