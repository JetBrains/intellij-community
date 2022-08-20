
class CL {
    void g() {
        int j;
        if (1 == 1)
        {
            j = 9;
            <caret>String laugh = "haha";
        }
        if (j > 10)
            s = "too high";
        else
            s = "too low";
    }
}
