// "Create Field For Parameter 'p1'" "true"

class Test{
    private final int myP1;
    int myP2;
 
    Test(int p<caret>1, int p2){
        myP1 = p1;
        myP2 = p2;
    }
}

