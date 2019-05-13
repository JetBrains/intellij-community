import java.util.Arrays;

class Test {
    Object[] myOs;
    int[][] myIIs;
    int[] myIs;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(myOs, test.myOs)) return false;
        if (!Arrays.deepEquals(myIIs, test.myIIs)) return false;
        if (!Arrays.equals(myIs, test.myIs)) return false;

        return true;
    }

    public int hashCode() {
        int result = Arrays.hashCode(myOs);
        result = 31 * result + Arrays.deepHashCode(myIIs);
        result = 31 * result + Arrays.hashCode(myIs);
        return result;
    }
}