class A {
    private class Inner {
        public void innerMethod() {
            new Runnable() {
                public void run() {
                    if (true) {
                        if (true) {
                            int i = 0;
                            int temp = i * i;
                            if (i == 0) {
                                System.out.println("" + temp);
                            } else {
                                System.out.println("" + temp);
                                i = temp;
                            }
                        }
                    }
                }
            }.run();
        }
    }
}