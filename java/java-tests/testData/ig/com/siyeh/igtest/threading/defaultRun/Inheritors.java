package com.siyeh.igtest.threading.defaultRun;
class NewThread {
  public static void main(String[] args) {
    Thread myThread = new <warning descr="Instantiating a 'MyThread' with default 'run()' method">MyThread</warning>(); //warn
    Thread myThread2 = new MyThread2();
    Thread myThread3 = new MyThread3();
    Thread myThread4 = new MyThread4();
    Thread myThread5 = new <warning descr="Instantiating a 'MyThread5' with default 'run()' method">MyThread5</warning>(); //warn
  }

  static class MyThread extends Thread {
    public void foo() {
      System.out.println("Hello");
    }
  }

  static class MyThread2 extends Thread {
    public MyThread2() {
    }

    @Override
    public void run() {
      super.run();
    }
  }

  static class MyThread3 extends Thread {
    public MyThread3() {
      super(new Runnable() {
        @Override
        public void run() {

        }
      });
    }
  }

  static class MyThread4 extends MyThread3 {

  }

  static class MyThread5 extends MyThread {
    public int run(int i){
      return i;
    }
  }
}
