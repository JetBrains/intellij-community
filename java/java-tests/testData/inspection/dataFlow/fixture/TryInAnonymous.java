class Bar {

    void submit(Runnable r) { }
    String[] foos() { return new String[0]; }

    void navigateTo() {
        submit(new Runnable() {
            public void run() {
                try {
                    for (Object next : foos()) {
                        if (next != null) {
                        }
                    }
                }
                catch (Throwable th) {
                }
            }
        });
    }

}
