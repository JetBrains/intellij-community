class A {
  private Object b = new MyException("w");

  private class <caret>MyException implements Runnable {
    public MyException(String...msg){
      this(new Throwable(), msg[0]);
    }

    public MyException(Throwable t, String msg)
    {
      System.out.println(msg);
    }

    public void run(){}
  }
}