class Main {
  public static void main(String[] args) {
    final class First{}
    class Second extends <error descr="Cannot inherit from final class 'First'">First</error>{}
  }
}