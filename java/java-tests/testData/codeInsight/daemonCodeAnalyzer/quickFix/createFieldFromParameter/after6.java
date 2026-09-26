// "Create field for parameter 'p1'" "true-preview"

class Test{
    private int myP3;
    int myP1;
    int myP2;
 
    void f(int p1, int p2){
        myP3 = p1;
        int myP2 = p1;
        p1 = 0;
    }
}

