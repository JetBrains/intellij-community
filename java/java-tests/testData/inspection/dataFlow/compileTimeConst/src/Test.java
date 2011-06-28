public class Y {   
    private static final boolean CONST = true;

    public void foo(boolean param) {
        boolean b = CONST || param;
        if (b) {
        }
    }
}
