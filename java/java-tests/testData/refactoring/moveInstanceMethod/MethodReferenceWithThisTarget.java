import java.util.function.Consumer;

class Source {
  public void f<caret>oo(Destination destination) {
    Consumer<String> doSomething = destination::doSomething;
  }
}

class Destination {
  public void doSomething(String s) {
  }
}