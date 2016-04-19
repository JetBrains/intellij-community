// "Replace with 'D.FontType.BOLD | D.FontType.ITALIC | D.FontType.WEIRD'" "true"
import org.intellij.lang.annotations.MagicConstant;

class D {
    static class FontType {
        public static final int PLAIN = 0;
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
        public static final int WEIRD = 4;
    }
    void font(@MagicConstant(flagsFromClass = FontType.class) int x) {
        font(<caret>FontType.BOLD | FontType.ITALIC | FontType.WEIRD);
    }
}