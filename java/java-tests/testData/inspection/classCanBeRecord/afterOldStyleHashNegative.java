// "Convert to a record" "true"
record Test(double[] arrayValue) {
    @Override
    public int hashCode() {
        int result = 17;

        result = 37 * result + Arrays.hashCode(arrayValue);

        return result;
    }
}