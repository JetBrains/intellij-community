import java.util.Arrays;

class A {
  int value;
  int[] values;
  int[][] valueses;

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof A)) return false;

        final A a = (A) o;
        return value == a.value &&
                Arrays.equals(values, a.values) &&
                Arrays.deepEquals(valueses, a.valueses);
    }

    @Override
    public int hashCode() {
        int result = value;
        result = 31 * result + Arrays.hashCode(values);
        result = 31 * result + Arrays.deepHashCode(valueses);
        return result;
    }
}