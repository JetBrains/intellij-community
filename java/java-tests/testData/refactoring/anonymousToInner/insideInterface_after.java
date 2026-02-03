interface M {
  Runnable r = new MyRunnable();

    class MyRunnable implements Runnable {
        @Override
        public void run() {
    
        }
    }
}