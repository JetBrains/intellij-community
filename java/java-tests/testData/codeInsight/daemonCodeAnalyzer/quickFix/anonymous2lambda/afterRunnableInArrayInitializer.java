// "Replace with lambda" "true"
class Test {
  {
    Runnable[] r = new Runnable[] {() -> System.out.println("")};
  }
}