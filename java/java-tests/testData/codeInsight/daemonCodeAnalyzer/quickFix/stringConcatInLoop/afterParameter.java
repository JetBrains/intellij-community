// "Introduce new StringBuilder to update variable 's'" "true"

public class Main {
    void test(String s) {
        StringBuilder sBuilder = new StringBuilder(s);
        for(int i = 0; i<10; i++) {
            sBuilder.append(i);
        }
        s = sBuilder.toString();
        System.out.println(s);
    }
}