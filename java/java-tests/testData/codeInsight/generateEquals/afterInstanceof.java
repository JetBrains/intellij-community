import java.util.Arrays;

class A {
  int value;
  int[] values;
  int[][] valueses;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof A)) return false;

        final A a = (A) o;

        if (value != a.value) return false;
        if (!Arrays.equals(values, a.values)) return false;
        if (!Arrays.deepEquals(valueses, a.valueses)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = value;
        result = 31 * result + Arrays.hashCode(values);
        result = 31 * result + Arrays.deepHashCode(valueses);
        return result;
    }
}