// "Assert 'foo != null'" "true-preview"
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class X {
  class Foo implements AutoCloseable {
    public void close() {}
  }
  class Bar implements AutoCloseable {
    Bar(@NotNull Foo foo) {}
    public void close() {}
  }
  
  native @Nullable Foo getFoo();
  
  void test() {
    try (Foo foo = getFoo();
         Bar bar = new Bar(f<caret>oo))
    {
      System.out.println(bar);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    finally {
      System.out.println("exiting");
    }
  }
}