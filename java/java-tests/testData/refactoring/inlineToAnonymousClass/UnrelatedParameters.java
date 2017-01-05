class Test {
  class <caret>A implements Runnable {
    public A(int[] args) {

      for (int arg : args) {
        System.out.println(arg);
      }
    }


    @Override
    public void run() {

    }
  }

  abstract class U {
    {
      A a = new A(new int[8]);
    }
  }

}