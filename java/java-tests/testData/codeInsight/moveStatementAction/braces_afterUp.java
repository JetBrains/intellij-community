public class Braces {
    public void meth(int j) {
        String s;
        if (j > 10) {
            s = "too high";
            <caret>String laugh = "haha";
        }
        else
        {
            s = "too low";
        }
    }
}
