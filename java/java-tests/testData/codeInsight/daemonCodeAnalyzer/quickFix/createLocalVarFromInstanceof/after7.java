// "Insert '(String)s' declaration" "true"
class C {
    void f() {
        String s = "";
        if (s instanceof String) {
            String s1 = (String) s;
            <caret>
        }
    }
}
