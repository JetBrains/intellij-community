// "Introduce new StringBuilder to update variable 's'" "true"

public class Main {
    void test(boolean b) {
        String s = "";
        if (b) {
            StringBuilder sBuilder = new StringBuilder();
            for(int i = 0; i<10; i++) {
                if(sBuilder.indexOf("a") > 0) {
                    System.out.println(sBuilder);
                    break;
                }
                sBuilder.append(i);
            }
            s = sBuilder.toString();
        }
        s = s.trim();
        System.out.println(s);
    }
}