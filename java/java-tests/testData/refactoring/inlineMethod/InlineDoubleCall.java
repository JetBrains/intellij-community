import java.util.List;

public class Foo {

  public Foo hi() {
    System.out.println("hi");
    return this;
  }

  public Foo hiTwice() {
    return hi().hi();
  }

  public static void main(String[] args) {
    new Foo().hi().<caret>hiTwice().hi();
  }
}