class C {
    {
        int i;

        newMethod(i);
        newMethod(128);
    }

    private void newMethod(int i) {
        System.out.println(i);
    }
}