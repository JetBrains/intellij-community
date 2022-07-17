class X {
    enum E {A}

    void print(E e) {
        int p = switch (e) {
            case A -> ans<caret>wer();
        }
    }

    int answer() {
        return 42;
    }
}