// "Move up into 'if' statement branches" "false"
public class Sample {
    Runnable runnable = new Runnable() {
        int field;

        @Override
        public void run() {
            class X {
                void test(int a) {
                    if (a > 0) {
                        String field = "";
                    }
                    else {

                    }
                    System.out.pri<caret>ntln(field);
                }
            }
        }
    };
}
