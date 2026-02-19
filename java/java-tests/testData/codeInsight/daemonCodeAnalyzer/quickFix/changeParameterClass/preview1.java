// "Make 'a' implement 'java.lang.Runnable'" "true-preview"
class a implements Runnable {
    void f(Runnable r) {
        f(this);
    }

    @Override
    public void run() {

    }
}