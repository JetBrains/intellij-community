// "Introduce new StringBuilder to update variable 's'" "true"

public class Main {
    void test(String s) {
        for(int i=0; i<10; i++) {
            s+<caret>=i;
        }
        System.out.println(s);
    }
}