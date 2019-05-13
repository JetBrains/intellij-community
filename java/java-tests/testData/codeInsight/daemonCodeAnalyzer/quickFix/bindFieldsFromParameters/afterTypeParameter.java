// "Bind method parameters to fields" "true"

class A{
    private String myP1;
    private String myP2;

    <T extends String> void f(T p1, T p2){
        myP1 = p1;
        myP2 = p2;
    }
}

