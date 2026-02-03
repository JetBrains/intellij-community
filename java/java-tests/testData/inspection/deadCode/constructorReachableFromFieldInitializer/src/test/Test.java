package test;

public class Test {

    public Test(Matcher m) {
        BracePair[] bracePairs = m.getPairs();
    }

    public static void main(String[] args) {
        System.out.println(new Test(new AMatcher()));
    }
}
