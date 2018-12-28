// "Assign parameter to field 'myStr'" "false"


class Foo1 {
    final String myStr;
    Foo1(String str, int i) {
        myStr = (str);
    }

    Foo1(String st<caret>r) {
        this(str, 2);
    }
}