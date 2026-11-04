// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    void test(@Nullable String s) {
        if (s.<caret>isEmpty()) System.out.println(1);
        else System.out.println(2);
    }
}
