// "Create Field For Parameter 'p1'" "true"

class Test{
    private final String myP1;

    <T extends String> void f(T p1){
        myP1 = p1;
    }
}

