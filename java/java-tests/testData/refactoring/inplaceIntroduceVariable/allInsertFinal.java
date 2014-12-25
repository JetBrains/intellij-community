class C {
    {
        C c = new <caret>C();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                new C();
            }
        };
    }
}