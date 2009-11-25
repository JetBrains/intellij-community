class A {
    private class Inner {
        public void innerMethod() {
            new Runnable() {
                public void run() {
                    if (true) {
                        if (true) {
                            int i = 0;
                            if (i == 0) {
                                System.out.println("" + <selection>i * i</selection>);
                            } else {
                                System.out.println("" + i * i);
                                i = i * i;
                            }
                        }
                    }
                }
            }.run();
        }
    }
}