
final class Bug
   extends JFrame {
    public Bug() {
        foo();
    }

    public void <caret>foo() {
        addWindowListener(new MyWindowListener());
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