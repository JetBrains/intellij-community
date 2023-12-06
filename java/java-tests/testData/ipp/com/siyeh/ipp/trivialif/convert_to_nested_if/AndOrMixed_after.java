public class Test {
    boolean A, B, C;
    boolean f() {
        <caret>if (A) if (B) return true;
        if (C) return true;
        return false;
    }
}
