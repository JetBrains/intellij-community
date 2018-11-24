// "Fix all ''compare()' method can be used to compare primitives' problems in file" "true"
public class Test {
    public int test(String s1, String s2) {
        int res = new Integer(s1.length()).co<caret>mpareTo(s2.length());
        if(res == 0) {
            res = new Character(/*reverse order!*/s2.charAt(0)).compareTo(s1.charAt(0));
        }
        return res;
    }
}