// "Invert 'if' condition" "true"
class A {
    
    void method(Object x) {
        if (!<caret>(x instanceof String)) {
            System.out.println(x.toString());
        } else {
            System.out.println(((String) x).toUpperCase());
        }
    }

}