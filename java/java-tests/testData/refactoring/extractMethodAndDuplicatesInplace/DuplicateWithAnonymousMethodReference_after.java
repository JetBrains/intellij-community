public class DuplicateWithAnonymousMethodReference {

    void test(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                extracted();

                extracted();
            }

            private void extracted() {
                local();
                System.out.println();
            }

            public void local() {
            }
        };
    }
}
