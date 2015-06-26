interface Container<T extends Content> extends Iterable<T> {}
interface Content {}

class Main {
  public static void doSomething(Container container) {
    for (<error descr="Incompatible types. Found: 'Content', required: 'java.lang.Object'">Content content</error> : container) {}
  }
}