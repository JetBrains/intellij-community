// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    void test(String[] strings) {
        String res = "";
        for (String s : strings) {
            if(res.length()+s.length() > 80) {
                System.out.println(res);
                res = "";
            }
            res<caret>+=s;
        }
        System.out.println(res);
    }
}
