class s implements Runnable {
    public void run() {
    }
}

class Over extends s {
    public void run() {
        if (true) {
            super.run();
        }
    }
}