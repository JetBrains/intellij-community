// "Replace with lambda" "false"
class Foo11 {
  Runnable runnable = new Runnable() {
    public void run() {
      int x = 5;
      new Runn<caret>able() {
        public void run () {
          int x = 10;
        }
      } ;
    }
  };
}