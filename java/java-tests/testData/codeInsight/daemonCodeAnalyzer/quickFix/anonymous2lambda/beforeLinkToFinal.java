// "Replace with lambda" "false"
class MyTest {
        final Runnable anonymRunnable = new Run<caret>nable() {
            @Override
            public void run() {
                System.out.println(o);
            }
        };

        final Object o;

        MyTest(Object o) {
            this.o = o;
        }
    }