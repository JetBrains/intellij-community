
class CL {
    void g() {
        int j;
        if (1 == 1)
            j = 9;
        j <caret>= 0;
        while (j==0)
            j = 2;
    }
}
