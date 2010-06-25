// "Make 'a' implement 'java.lang.Runnable'" "true"
class a implements Runnable {
    void f(Runnable r) {
        f(this);
    }

    public void run() {
        <caret><selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}

