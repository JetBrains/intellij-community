// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            case 1:
                System.out.println("hello");
                break;
            <caret>case 2:
                System.out.println("hello");
                break;
        }
    }
}