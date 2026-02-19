// "Create field for parameter 'p1'" "true-preview"

class Test{
    private final int <caret>myP1;
    int myP2;
 
    Test(int p1, int p2){
        myP1 = p1;
        myP2 = p2;
    }
}

