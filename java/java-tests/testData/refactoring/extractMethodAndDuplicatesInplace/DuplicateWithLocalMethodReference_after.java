public class DuplicateWithLocalMethodReference {

    void example(){}

    void test(){
        extracted();

        extracted();
    }

    private void extracted() {
        System.out.println();
        example();
    }
}