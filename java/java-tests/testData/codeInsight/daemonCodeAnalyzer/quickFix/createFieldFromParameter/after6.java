// "Create field for parameter 'p1'" "true-preview"

class Test{
    int myP1;
    int myP2;
    private int myP11;

    void f(int p1, int p2){
        myP11 = p1;
        int myP2 = p1;
        p1 = 0;
    }
}

