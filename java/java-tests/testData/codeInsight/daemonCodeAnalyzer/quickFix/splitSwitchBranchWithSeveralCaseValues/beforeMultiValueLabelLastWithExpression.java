// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            case 1, 1+1<caret>:
                System.out.println("hello");
                break;
        }
    }
}