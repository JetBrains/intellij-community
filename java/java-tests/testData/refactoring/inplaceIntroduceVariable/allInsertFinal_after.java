class C {
    {
        C c1 = new C();
        C c = c1;
        Runnable r = new Runnable() {
            @Override
            public void run() {
                c1;
            }
        };
    }
}