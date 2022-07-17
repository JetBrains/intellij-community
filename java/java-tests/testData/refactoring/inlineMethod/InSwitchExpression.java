class X {
    enum E {A}

    void print(E e) {
        switch (e) {
            case A -> printT<caret>oInline(e);
        }
    }

    void printToInline(E e) {
        System.out.println(e);
    }
}