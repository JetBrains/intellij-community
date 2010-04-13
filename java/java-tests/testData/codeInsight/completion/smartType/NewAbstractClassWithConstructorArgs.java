public class Main {

  public static void main(String[] args) {
    Bar b = new B<caret>
  }

}

public abstract class Bar {
  protected Bar(int a) {
  }

  abstract void update();
}
