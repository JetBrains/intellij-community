// "Insert '(String)o' declaration" "false"
class C {
    void f(Object o, Object f) {
        if (o instanceof String || f == null) {
          <caret>
        }
    }
}