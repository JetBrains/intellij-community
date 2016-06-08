// "Replace with lambda" "true"

import java.util.function.Consumer;

class A {
  void anonymousToLambda(String s) {
    String s12 = "";
    Consumer<String> consumer = new Consu<caret>mer<String>() {
      @Override
      public void accept(final String s) {
        String s1 = "";
      }
    };
  }
}