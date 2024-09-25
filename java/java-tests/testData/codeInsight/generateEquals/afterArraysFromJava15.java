import java.util.Arrays;

class Test {
    Object[] myOs;
    int[][] myIIs;
    int[] myIs;

    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;
        return Arrays.equals(myOs, test.myOs) &&
                Arrays.deepEquals(myIIs, test.myIIs) &&
                Arrays.equals(myIs, test.myIs);
    }

    public int hashCode() {
        int result = Arrays.hashCode(myOs);
        result = 31 * result + Arrays.deepHashCode(myIIs);
        result = 31 * result + Arrays.hashCode(myIs);
        return result;
    }
}