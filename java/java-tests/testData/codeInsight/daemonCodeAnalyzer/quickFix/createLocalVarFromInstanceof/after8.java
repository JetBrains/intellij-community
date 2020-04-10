// "Insert '(String)o' declaration" "true"
class C {
    void f(Object o) {
        if (o instanceof String) {
            String s = (String) o;
            <caret>
            o = "";
        }
    }
}
