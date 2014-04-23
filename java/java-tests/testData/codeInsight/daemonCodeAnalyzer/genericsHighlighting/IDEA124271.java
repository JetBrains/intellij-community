
interface Container<T extends Content> extends Iterable<T> {}
interface Content {}

class Main {
  public static void doSomething(Container container) {
    for (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'Content'">Content content : container</error>) {}
  }
}