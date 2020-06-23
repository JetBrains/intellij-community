class Test {
    private List<Object> list1 = new ArrayList<Object>();

    private List<Object> list2 = new ArrayList<Object>();

    public void method1()
    {
        newMethod();
    }

    public void method2()
    {
        newMethod();
    }

    private void newMethod() {
        list1.add(null);
        // add to list2
        list2.add(true);
    }
}