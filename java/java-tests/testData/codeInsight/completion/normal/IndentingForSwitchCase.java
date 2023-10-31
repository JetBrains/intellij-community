class Test {
    void m(E e) {
        switch (e) {
            case AAA:
              //simple 'B' can be associated with Object, it is allowed started from Java 21
                case BB<caret>
        }
    }
}

enum E {
  AAA, BBB
}