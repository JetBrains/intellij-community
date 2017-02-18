
final class Bug
   extends JFrame {
    public Bug() {
        foo(this);
    }

    public static void foo(Bug anObject) {
        anObject.addWindowListener(anObject.new MyWindowListener());
    }

    private class MyWindowListener
       extends WindowAdapter {
        public void windowActivated(int e) {
        }
    }
}

class JFrame {
    public void addWindowListener(WindowAdapter e) {}
    static class WindowAdapter {
        public void windowActivated(int e) {
        }
    }
}