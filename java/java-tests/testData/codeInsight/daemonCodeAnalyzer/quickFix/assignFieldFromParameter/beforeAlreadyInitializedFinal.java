// "Assign parameter to field 'myStr'" "false"


class Foo1 {
    final String myStr;
    Foo1(String s<caret>tr) {
        if(Math.random() > 0.5) {
            myStr = "foo";
        } else {
            myStr = "bar";
        }
    }
}