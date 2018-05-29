// "Create field for parameter 'p1'" "true"

class Test{
    int b;
    @org.jetbrains.annotations.NotNull
    private final String myP1;

    Test(String p1){
        b = p1.length();
        myP1 = p1;
    }
}

