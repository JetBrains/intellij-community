// "Create field for parameter 'p1'" "true-preview"

class Test{
    private String myP1;

    <T extends String> void f(T p1){
        myP1 = p1;
    }
}

