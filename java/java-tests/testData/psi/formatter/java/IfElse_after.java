
public class Foo {
    public void foo(boolean a, int x,
                    int y, int z) {
        label1:
        do {
            try {
                if (x > 0) {
                    int someVariable = a ?
                                       x :
                                       y;
                }
                else
                    if (x < 0) {
                        int someVariable = (y +
                                            z
                        );
                        someVariable = x =
                                x +
                                y;
                    }
                    else {
                        label2:
                        for (int i = 0;
                             i < 5;
                             i++) { doSomething(i); }
                    }
                switch (a) {
                    case 0:
                        doCase0();
                        break;
                    default:
                        doDefault();
                }
            }
            catch (Exception e) {
                processException(e.getMessage(),
                                 x + y, z, a);
            }
            finally {
                processFinally();
            }
        }
        while (true);

        if (2 < 3) return;
        if (3 < 4)
            return;
        do x++ while (x < 10000);
        while (x < 50000) x++;
        for (int i = 0; i < 5; i++) System.out.println(i);
    }

    private class InnerClass implements I1,
                                        I2 {
        public void bar() throws E1,
                                 E2 {
        }
    }
}