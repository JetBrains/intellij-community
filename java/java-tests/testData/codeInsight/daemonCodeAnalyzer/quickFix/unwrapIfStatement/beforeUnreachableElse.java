// "Unwrap 'if' statement" "true"
class X {
    private final boolean field = true;

    String m() {
        if (fiel<caret>d) return "one";
        return "two";
    }
}