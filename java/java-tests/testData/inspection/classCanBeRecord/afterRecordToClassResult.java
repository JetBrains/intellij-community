// "Convert to record class" "true-preview"

import java.util.Objects;

record R(Object a, String b, int c, double d, float e, int[] arr) {

    @Override
    public String toString() {
        return "R[" +
                "a=" + a + ", " +
                "b=" + b + ", " +
                "c=" + c + ", " +
                "d=" + d + ", " +
                "e=" + e + ", " +
                "arr=" + arr + ']';
    }
}
