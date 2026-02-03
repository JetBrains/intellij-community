public class Test {

    void foo() {
        EEnum i = foobar(false);
        switch (i) {
            case FOO:
                break;
            case BAR:
                break;
        }
    }

    EEnum foobar(boolean flag) {
        if (flag) {
            return EEnum.FOO;
        }
        return EEnum.BAR;
    }
}