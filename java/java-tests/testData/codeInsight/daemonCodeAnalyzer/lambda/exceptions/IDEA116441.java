import java.io.IOException;

class Main48 {
    public static void main(String[] args) throws Exception {
        templateMethod(( ) -> {
            throw new IOException();
        });
    }

    static void templateMethod(RunnableWithException<IOException> r) {
        try {
            r.run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    interface RunnableWithException<E extends Exception> {
        void run() throws E;
    }
}