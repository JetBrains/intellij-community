// "Convert to record class" "true-preview"
record R(int first, String second, int... third, boolean fourth) {

    private int getFirst() {
        return first > 0 ? first : -first;
    }

    String getSecond() {
        return second.length() > 1 ? second : "";
    }

    private int[] getThird() {
        return third;
    }

    boolean isFourth() {
        return fourth;
    }
}
