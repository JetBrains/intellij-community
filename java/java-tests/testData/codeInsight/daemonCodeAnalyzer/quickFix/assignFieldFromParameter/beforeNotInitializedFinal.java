// "Assign parameter to field 'myStr'" "true-preview"


class Foo1 {
    final String myStr;
    Foo1(String s<caret>tr) {
        if(Math.random() > 0.5) {
        } else {
        }
    }
}