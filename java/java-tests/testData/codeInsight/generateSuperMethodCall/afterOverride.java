class s implements Runnable {
    public void run() {
    }
}

class Over extends s {
    public void run() {
        <caret>super.run();
    }
}