import java.util.Collection;
import java.util.List;

class Idea {
  class Library<T> {
    public void f(Base<T> x) {
    }
    public void f(Derived<T> x) {
    }
  }

  class Wrapper<T> {
  }

  class Base<In> {
  }
  class Derived<In> extends Base<In> {
  }

  public void main(Derived<Wrapper<String>> x) {
    new Library<Wrapper<String>>().f(x);
  }
}