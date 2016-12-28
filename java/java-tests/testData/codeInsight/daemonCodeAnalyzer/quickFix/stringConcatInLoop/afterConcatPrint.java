// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    void test(String[] strings) {
        StringBuilder res = new StringBuilder();
        for (String s : strings) {
            if(res.length()+s.length() > 80) {
                System.out.println(res);
                res = new StringBuilder();
            }
            res.append(s);
        }
        System.out.println(res);
    }
}
