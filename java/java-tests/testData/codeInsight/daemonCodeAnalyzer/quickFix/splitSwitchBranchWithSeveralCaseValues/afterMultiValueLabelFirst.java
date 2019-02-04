// "Split values of 'switch' branch" "true"
class C {
    void test(int i) {
        switch (i) {
            case 2:
                System.out.println("hello");
                break;
            <caret>case 1:
                System.out.println("hello");
                break;
        }
    }
}