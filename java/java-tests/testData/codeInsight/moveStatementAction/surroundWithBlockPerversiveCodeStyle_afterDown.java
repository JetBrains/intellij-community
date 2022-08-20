
class CL {
    void g() {
        int j;
        if (1 == 1)
            j = 9;
        if (j > 10)
        {
            <caret>String laugh = "haha";
            s = "too high";
        }
        else
            s = "too low";
    }
}
