// "Replace with 'FontType.ITALIC'" "true"
import org.intellij.lang.annotations.MagicConstant;

class D {
    static class FontType {
        public static final int PLAIN = 0;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
    }
    private static final int UNDER = 2;
    void font(@MagicConstant(flags = {FontType.PLAIN, FontType.BOLD, FontType.ITALIC}) int x) {
        font(<caret>UNDER);
    }
}