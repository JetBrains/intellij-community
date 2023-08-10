class Foo {
    void foo(String <caret>s) {
    }

    void bar() {
        final String res;
        res = <flown11>"a";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                foo(<flown1>res);
            }
        });
    }
}
class SwingUtilities {
  static void invokeLater(Runnable runnable) {}
}