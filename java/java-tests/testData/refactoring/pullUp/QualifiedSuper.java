class <caret>QualifiedSuper extends Base {
    class Inner {
        void goo() {
            QualifiedSuper.super.toString();
        }
    }
}

class Base {


}
