import java.util.Arrays;

// "Collapse into loop" "true"
class X {
    void test() {
        for (String s : Arrays.asList("Hello", "Hello1", "Hello2")) {
            System.out.println(s);
        }
        System.out.println("Hello");
        System.out.println("Hello");
        System.out.println("Hello");
    }
}