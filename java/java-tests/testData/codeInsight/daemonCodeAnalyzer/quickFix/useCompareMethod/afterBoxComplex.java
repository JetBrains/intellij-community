// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
public class Test {
    public int test(String s1, String s2) {
        int res = Integer.compare(s1.length(), s2.length());
        if(res == 0) {
            /*reverse order!*/
            res = Character.compare(s2.charAt(0), s1.charAt(0));
        }
        return res;
    }
}