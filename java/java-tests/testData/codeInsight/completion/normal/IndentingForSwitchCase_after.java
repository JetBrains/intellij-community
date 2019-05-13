class Test {
    void m(E e) {
        switch (e) {
            case AA:
            case BB:<caret>
        }
    }
}

enum E {
  AA, BB
}