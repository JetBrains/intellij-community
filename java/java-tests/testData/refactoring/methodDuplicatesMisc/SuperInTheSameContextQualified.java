public class a {
    public void login() {
        System.out.println();
    }
}

class b extends a {
    public void doL<caret>ogin() throws Exception {
        super.login();
    }

    public void foo() throws Exception {
      new Runnable() {
        public void run() {
          b.super.login();
        }
      }.run();
    }
}

