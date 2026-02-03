// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 2 -> bar("B");
            //comment1
            default ->bar("A");//comment2
        }
    }
    void bar(String s){}
}