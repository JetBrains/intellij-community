// "Replace static import with qualified access to Math" "true-preview"
import static java.lang.Math.P<caret>I;

// one
// two

public class MyMath {
    static void main() {
        System.out.println(PI);
    }
}
