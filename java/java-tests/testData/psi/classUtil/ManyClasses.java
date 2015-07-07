public class ManyClasses {
  public void foo() {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        Comparable<Integer> c = new Comparable<Integer>() {
          @Override
          public int compareTo(Integer o) {
            return 0;
          }
        };
      }
    };
    
    class FooLocal {
      Runnable r = new Runnable() {
        @Override
        public void run() { }
      };
    }
  }
  
  public void bar() {
    class FooLocal implements Runnable {
      @Override
      public void run() { }
    }
  }
  
  public class Child { }
}

class Local {
  public static class Sub { }
}

class Local$ {
  public static class Sub { }
}