class Test {
    void m(E e) {
        switch (e) {
            case AA:
                case B<caret>
        }
    }
}

enum E {
  AA, BB
}