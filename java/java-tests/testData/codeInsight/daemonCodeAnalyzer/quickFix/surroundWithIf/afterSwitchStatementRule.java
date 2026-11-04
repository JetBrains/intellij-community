// "Surround with 'if (s != null)'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
    void test(@Nullable String s, int k) {
        switch (k) {
            case 1 -> {
                if (s != null) {
                    System.out.println(s.length());
                }
            }
            default -> {}
        }
    }
}
