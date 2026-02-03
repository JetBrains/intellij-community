// "Insert '(String)o' declaration" "true-preview"
class C {
    void f(Object o, Object f) {
        if (o instanceof String && f == null) {
            String s = (String) o;
            <caret>
        }
    }
}