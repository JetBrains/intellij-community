// "Split values of 'switch' branch" "true"
class C {
    void test(int i) {
        switch (i) {
            case 1:
                System.out.println("hello");
                break;
            case 1 + 1:
                System.out.println("hello");
                break;
        }
    }
}