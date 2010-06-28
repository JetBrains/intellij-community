// "Create Field 'array'" "true"
class Smth<T> {
}

class Converter {
  static <T> Smth<T> asSmth (T[] t) {}
}

class Test {
    private String[] array<caret>;

    void bar () {
        Smth<String> l = Converter.asSmth(array);
    }
}