// "Replace with 'FontType.PLAIN'" "true"
import org.intellij.lang.annotations.MagicConstant;

class D {
    static class FontType {
        public static final int PLAIN = 0;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
    }
    void font(@MagicConstant(flags = {FontType.PLAIN, FontType.BOLD, FontType.ITALIC}) int x) {
        // 0 is not allowed despite the fact that it's flags parameter
        font(<caret>FontType.PLAIN);
    }
}