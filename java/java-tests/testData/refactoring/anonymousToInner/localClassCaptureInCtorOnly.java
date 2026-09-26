import java.util.function.IntFunction;

public class LocalClass {
    void test(int x, int y) {
        class <caret>Cls {
            Cls() {
                System.out.println(x);
            }
            
            Cls(int... data) {
                System.out.println(y);
            }
        }
        
        new Cls();
        new Cls(1,2,3);
    }
}
