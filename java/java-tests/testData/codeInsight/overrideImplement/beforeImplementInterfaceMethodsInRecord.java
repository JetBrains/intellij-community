record R() implements Nameable, Sizable {
    <caret>
}

interface Nameable {
    String name();

    String lastName();
}

interface Sizable {
    int size();
}