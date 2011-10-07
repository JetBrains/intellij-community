class C{
    /**
     * @see A#<caret>
     */
    private int myField;
    
    class A{  private String myName;  private int foo(int a, char b, String c) {} }
    class B{}
}