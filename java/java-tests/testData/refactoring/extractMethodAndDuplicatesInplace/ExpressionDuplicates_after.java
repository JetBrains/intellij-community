public class Test {
    void f1(String p) {
        String r = getR(p);
    }

    private String getR(String p) {
        return p.stripIndent();
    }

    void f2(String p) {
        String r = getR(p);
    }
}