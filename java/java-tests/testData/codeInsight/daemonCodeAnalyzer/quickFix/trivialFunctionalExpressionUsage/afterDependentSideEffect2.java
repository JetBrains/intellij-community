// "Replace method call on lambda with lambda body" "true-preview"
import java.util.function.IntBinaryOperator;

public class Main {
    public static void main(String[] args) {
        int l = 1;
        int q = 2;
        System.out.println(l + " " + q);
        int i = q;
        q++;
        l = i;
        System.out.println(l + " " + q);
    }
}
