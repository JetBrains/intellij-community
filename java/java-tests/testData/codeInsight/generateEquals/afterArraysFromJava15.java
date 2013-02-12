import java.util.Arrays;

class Test {
    Object[] myOs;
    int[][] myIIs;
    int[] myIs;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Test test = (Test) o;

        // Compare nested arrays - values of myIIs here
        if (!Arrays.equals(myIs, test.myIs)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(myOs, test.myOs)) return false;

        return true;
    }

    public int hashCode() {
        int result = myOs != null ? Arrays.hashCode(myOs) : 0;
        result = 31 * result + (myIIs != null ? Arrays.hashCode(myIIs) : 0);
        result = 31 * result + (myIs != null ? Arrays.hashCode(myIs) : 0);
        return result;
    }
}