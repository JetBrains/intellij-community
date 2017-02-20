import java.util.Arrays;

// "Replace loop with Arrays.setAll" "true"
public class Test {
    private Object[] data;

    public void fill() {
        /*in body*/
        /*in lvalue*/
        Arrays.setAll(this./*comment*/data, idx -> "Hello!" + /* we need index here */ idx);
    }
}