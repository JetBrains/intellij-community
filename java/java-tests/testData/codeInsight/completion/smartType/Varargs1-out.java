public class Varargs {
    void foo (String s, String ... objs) {}

    void bar () {
        String sss = new String();
        foo ("", "", sss<caret>);
    }
}