public class Main {
  public static void main(String[] args) {
    Something<?> s = new SomethingBuilder().buildSomething();
    s.cast<caret>
  }
}

class Something<T> {}

class SomethingBuilder {
  Something buildSomething() {
    return new Something<String>();
  }
}