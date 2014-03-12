class Main {
  interface MyCall {
    void call(int n);
  }
  interface MyCallRet {
    int call(int n);
  }
  public static void caller(MyCall c) {
    c.call(2);
  }
  public static void caller(MyCallRet c) {
    c.call(3);
  }
  public static void main(String[] args) {
    caller( (int n) -> { System.out.println(" " + n);  } );
  }
}