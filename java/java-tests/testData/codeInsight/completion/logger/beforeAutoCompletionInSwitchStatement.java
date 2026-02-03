public class A {
    void foo() {
        String x = "";
        switch (x) {
          case "name": lo<caret>
          default: throw new Throwable()
        }
    }
}