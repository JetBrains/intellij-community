record R() implements Nameable, Sizable {
    @Override
    public String name() {
        return null;
    }

    @Override
    public String lastName() {
        return null;
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