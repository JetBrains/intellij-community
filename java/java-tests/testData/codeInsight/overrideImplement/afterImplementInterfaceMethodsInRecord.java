record R() implements Nameable, Sizable {
    @Override
    public String name() {
        return "";
    }

    @Override
    public String lastName() {
        return "";
    }

    @Override
    public int size() {
        return 0;
    }
}

interface Nameable {
    String name();

    String lastName();
}

interface Sizable {
    int size();
}