// "Split values of 'switch' branch" "true"
class C {
    void test(int i) {
        switch (i) {
            case 1:
                System.out.println("hello");
                break;
            case 2:
                System.out.println("hello");
                break;
        }
    }
}