// "Delete redundant 'switch' branch" "INFORMATION"
class C {
    void foo(int n) {
        switch (n) {
            case 2 -> {
                bar("B");
            }
            default -> {
                /*comment 2*/
                bar("A");
                /*comment 1*/
            }
            /*comment 3*/
        }
    }
    void bar(String s){}
}