public class Test {
  public static void main(String[] args) {
    final Memento m = () -> isPlaying;
    System.out.println(m);
  }

  public static interface Memento {
    boolean isPlaying();
  }
}