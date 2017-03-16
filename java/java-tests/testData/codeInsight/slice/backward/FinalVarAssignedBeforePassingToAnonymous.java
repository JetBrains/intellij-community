import javax.swing.*;

class Foo {
    void foo(String <caret>s) {
    }

    void bar() {
        final String <flown11>res;
        res = <flown111>"a";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                foo(<flown1>res);
            }
        });
    }
}