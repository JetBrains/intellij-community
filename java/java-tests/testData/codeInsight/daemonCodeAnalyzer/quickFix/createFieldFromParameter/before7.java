// "Create Field For Parameter 'p1'" "false"

class Test{
    int myP1;
    int myP2;
 
    void f(int p<caret>1, int p2){
        myP2 = p1;
        p1 = 0;
    }
}

