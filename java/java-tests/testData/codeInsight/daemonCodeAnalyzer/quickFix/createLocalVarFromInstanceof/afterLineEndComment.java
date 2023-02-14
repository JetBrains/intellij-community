// "Insert '(String)o' declaration" "true-preview"
class C {
    void f(Object o, Object f) {
        if (o instanceof String) {//todo comment
            String s = (String) o;
            
        }
    }
}