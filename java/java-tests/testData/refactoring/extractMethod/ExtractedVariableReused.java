public class OutputVariableReused {

    static class X {
        X(String s) {}
    }

    String convert(String s, String s1, String s2) {
        return s + s1 + s2;
    }

    public X test(String s, String left, String right) {
        <selection>String res = convert(s, left, right);
        if (res != null) {
            return new X(res);
        }</selection>
        res = convert(s, right, left);
        if (res != null) {
            return new X(res);
        }
        return null;
    }
}
