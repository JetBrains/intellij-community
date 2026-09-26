// "Invert 'if' condition" "true"
class Main {
    void foo(boolean a1, boolean b1){
        if (a1) {
            if (!b1) {
                System.out.println("1");
            }
        }
        else {
            System.out.println("2");
        }
    }
}