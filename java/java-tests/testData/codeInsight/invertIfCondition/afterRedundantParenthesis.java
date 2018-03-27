// "Invert 'if' condition" "true"
class A {
    
    void method(Object x) {
        if (x instanceof String) {
            System.out.println(((String) x).toUpperCase());
        }
        else {
            System.out.println(x.toString());
        }
    }

}