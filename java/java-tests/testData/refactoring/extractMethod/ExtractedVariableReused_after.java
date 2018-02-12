import org.jetbrains.annotations.Nullable;

public class OutputVariableReused {

    static class X {
        X(String s) {}
    }

    String convert(String s, String s1, String s2) {
        return s + s1 + s2;
    }

    public X test(String s, String left, String right) {
        X res1 = newMethod(s, left, right);
        if (res1 != null) return res1;
        String res;
        res = convert(s, right, left);
        if (res != null) {
            return new X(res);
        }
        return null;
    }

    @Nullable
    private X newMethod(String s, String left, String right) {
        String res = convert(s, left, right);
        if (res != null) {
            return new X(res);
        }
        return null;
    }
}
