
import java.util.concurrent.ExecutorService;

class Test {
    private void m(ExecutorService service, int i) {
        service.submit(() -> {
                switch (i) {
                    default:
                        break;
                }
        });
    }
}