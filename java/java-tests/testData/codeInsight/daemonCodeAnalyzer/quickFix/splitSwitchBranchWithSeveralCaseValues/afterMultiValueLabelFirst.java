// "Split values of 'switch' branch" "true-preview"
class C {
    void test(int i) {
        switch (i) {
            case 2:
                System.out.println("hello");
                break;
            case 1:
                System.out.println("hello");
                break;
        }
    }
}