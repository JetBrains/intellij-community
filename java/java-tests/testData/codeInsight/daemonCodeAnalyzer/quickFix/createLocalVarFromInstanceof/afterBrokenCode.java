// "Insert '(String)o' declaration" "true-preview"
class X {
    void foo(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            <caret>
            String substring = o.();
        }
    }
}