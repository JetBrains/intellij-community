public class Test {

    void foo(EEnum i) {
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
        }
        int k = Math.max(i.getValue() * i.getValue(), i.getValue() + i.getValue());
    }
}