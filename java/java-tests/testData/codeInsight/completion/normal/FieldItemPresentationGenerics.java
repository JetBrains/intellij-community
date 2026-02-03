class Ref<T> {
  T target;
}

class TestPoint2D {
    void (Ref<String> r) {
        r.<caret>

    }
}