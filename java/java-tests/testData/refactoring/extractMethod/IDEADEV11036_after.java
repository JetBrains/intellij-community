class MyClass {
    public void newMethod(long i) {
    }
    {
        int i = 0;
        newMethod(i);
        newMethod((long) 14);
    }

    private void newMethod(int i) {
        System.out.println(i);
    }
}