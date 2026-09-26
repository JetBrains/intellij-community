import java.io.InputStream;
import java.util.ArrayList;

public class PublicMethodInPrivateClass {
    public static void main(String[] args) {
        PrivateList privateList = new PrivateList();
        PrivateThread privateThread = new PrivateThread();
        PrivateInputStream privateInputStream = new PrivateInputStream();
        <caret>int a = 42;
    }

    private static class PrivateList extends ArrayList<Integer> {
        @Override
        public int size() {
            return super.size();
        }

        public boolean remove(Integer value) {
            return false;
        }
    }

    private static class PrivateThread extends Thread {
        public static int activeCount() {
            return -1;
        }
    }

    private static class PrivateInputStream extends InputStream {
        @Override
        public int read() {
            return -1;
        }
    }
}
