// "Convert to a record" "true"
record R(int first, String second, int... third) {

    @Override
    public int first() {
        return first > 0 ? first : -first;
    }

    @Override
    public String second() {
        return second.length() > 1 ? second : "";
    }
}