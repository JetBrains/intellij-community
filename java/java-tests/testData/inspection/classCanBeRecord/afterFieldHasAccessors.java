// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873
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
