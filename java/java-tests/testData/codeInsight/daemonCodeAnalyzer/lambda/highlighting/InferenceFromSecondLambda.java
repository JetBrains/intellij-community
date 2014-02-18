import java.util.*;
class Main1 {

    interface I<T> {
        List<T> f();
    }

    static class Test {
        <Z> void m(I<Z> i, I<Z> ii) {
        }

        <Z> void m(I<Z> s) {
        }

        {
            m(() -> emptyList(), () -> new ArrayList<String>());
            m(() -> new ArrayList<String>(), () -> emptyList());
            m((I<String>) () -> emptyList(), () -> new ArrayList<String>());
            m(() -> Test.<String>emptyList(), () -> new ArrayList<String>());
            m(() -> emptyList());
        }

        static <T> List<T> emptyList() {
            return null;
        }
    }
}
