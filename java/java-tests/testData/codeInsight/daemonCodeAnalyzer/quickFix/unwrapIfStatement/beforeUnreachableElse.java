// "Unwrap 'if' statement" "true-preview"
class X {
    String m() {
        boolean field = true;
        if (fiel<caret>d) return "one";
        return "two";
    }
}