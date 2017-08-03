// "Insert '(String)o' declaration" "true"
class X {
    void foo(Object o) {
        if (o instanceof String) {
            String o1 = (String) o;
            <caret>
            String substring = o.();
        }
    }
}