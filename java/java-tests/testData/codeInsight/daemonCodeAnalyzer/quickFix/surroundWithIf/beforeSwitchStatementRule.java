// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    void test(@Nullable String s, int k) {
        switch (k) {
            case 1 -> System.out.println(s.<caret>length());
            default -> {}
        }
    }
}
