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

  public class Child$ { }

  public class Ma$ked {
    public class Ne$ted { }
  }

  public class Edge { }

  public class Edge$ {
    public class $tu_pid_ne$s { }
  }
}

class Local {
  public static class Sub { }
}

class Local$ {
  public static class Sub { }
}