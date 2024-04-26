// "Delete redundant 'switch' branch" "INFORMATION"
class C {
    void foo(int n) {
        switch (n) {
            case 2:
                bar("B");
                break;
            default:
                /*comment 2*/
                bar("A");
                break;
                /*comment 1*/
            case 1:
                /*comment 2*/
                bar("A");<caret>
                /*comment 3*/
                break;
        }
    }
    void bar(String s){}
}