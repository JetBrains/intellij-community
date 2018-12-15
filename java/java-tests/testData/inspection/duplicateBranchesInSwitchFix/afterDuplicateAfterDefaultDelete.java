// "Delete redundant 'switch' branch" "GENERIC_ERROR_OR_WARNING"
class C {
    void foo(int n) {
        switch (n) {
            case 2:
                bar("B");
                break;
            default:
                /*comment 2*/
                bar("A");
                break;/*comment 1*/
            /*comment 3*/
        }
    }
    void bar(String s){}
}