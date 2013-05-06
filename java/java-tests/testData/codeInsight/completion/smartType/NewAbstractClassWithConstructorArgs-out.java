public class Main {

  public static void main(String[] args) {
    Bar b = new Bar() {
        @Override
        void update() {
        }
    };
  }

}

public abstract class Bar {
  protected Bar(int a) {
  }

  abstract void update();
}
