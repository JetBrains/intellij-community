// "Create field 'array'" "true-preview"
class Smth<T> {
}

class Converter {
  static <T> Smth<T> asSmth (T[] t) {}
}

class Test {
    void bar () {
        Smth<String> l = Converter.asSmth(a<caret>rray);
    }
}