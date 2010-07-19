public class ABl {
    void foo(int fe) {
        System.out.println(fe);
    }

    void bar(boolean br) {
        int fe = 0;
        if (br) {
            foo(fe);
        } else {
            f<caret>oo(11);
        }
    }
}
