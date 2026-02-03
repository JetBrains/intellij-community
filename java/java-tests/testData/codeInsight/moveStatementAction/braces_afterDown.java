public class Braces {
    public void meth(int j) {
        String s;
        if (j > 10)
            s = "too high";
        else
        {
            s = "too low";
            <caret>String laugh = "haha";
        }
    }
}
