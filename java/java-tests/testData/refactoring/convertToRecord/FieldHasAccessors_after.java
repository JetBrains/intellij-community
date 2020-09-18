record R(int first, String second, int... third) {
    R(int first, String second, int... third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public int first() {
        return first > 0 ? first : -first;
    }

    @Override
    public String second() {
        return second.length() > 1 ? second : "";
    }
}