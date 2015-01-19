interface Bar<T> { }

interface Base<T> { }

class Foo<T,U> implements Base<U> {
   public void ge<caret>t(Bar<U> bar) { }
}