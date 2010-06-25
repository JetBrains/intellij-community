// "Cast parameter to 'GeneralTest.B'" "true"
import java.util.*;
class GeneralTest {
    static class B {

    }
    static class A {
        A(B b) {
            System.out.println(b);
        }
    }
    public static void main(String[] args) {
        List a = new ArrayList();
        new A((B) a.get(0))<caret> {   

        };
    }
}

