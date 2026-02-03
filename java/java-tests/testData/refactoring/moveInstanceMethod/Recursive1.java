class FirstClass {
    void <caret>x(SecondClass sc) {
        if (sc != null) x(sc.g());
    }

    void y() {
        x(new SecondClass());
    }
}

class SecondClass {
    SecondClass g() { return null; }
}