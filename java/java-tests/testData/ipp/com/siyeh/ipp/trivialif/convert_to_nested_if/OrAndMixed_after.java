public class Test {
    boolean A, B, C;
    boolean f() {
        <caret>if (C) return true;
        if (A) if (B) return true;
        return false;
    }
}
