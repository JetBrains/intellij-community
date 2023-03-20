// "Insert '(String)o' declaration" "true-preview"
class C {
    void f(Object o) {
        if (o instanceof String<caret>)
        o = "";
    }
}
