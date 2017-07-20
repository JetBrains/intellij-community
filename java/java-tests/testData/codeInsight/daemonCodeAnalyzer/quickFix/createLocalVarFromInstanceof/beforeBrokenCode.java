// "Insert '(String)o' declaration" "true"
class X {
    void foo(Object o) {
        if (o insta<caret>nceof String) {
            String substring = o.();
        }
    }
}