// "Assign parameter to field 'myStr'" "true"


class Foo1 {
    final String myStr;
    Foo1(String s<caret>tr) {
        if(Math.random() > 0.5) {
        } else {
        }
    }
}