class C{
    /**
     * @see #perform(int, String) <caret>
     */
    private int myField;

    public void perform() {}
    public void perform(int a) {}
    public void perform(int a, String b) {}
    public void perform(String b) {}
}
