class Test {
  interface Task  {
    void doit();
  }

  class SuperClient {
    public static void main(String[] args) {
      Task t = () -> System.out.println("hello");
      Runnable r = <caret>t::doit;
    }
  }
}