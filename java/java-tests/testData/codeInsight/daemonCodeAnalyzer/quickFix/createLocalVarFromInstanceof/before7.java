// "Insert '(String)s' declaration" "true"
class C {
    void f() {
        String s = "";
        if (s instanceof String) {
            <caret>
        }
    }
}
