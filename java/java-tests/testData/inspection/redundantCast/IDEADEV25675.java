class Test {
    public void foo(Object[] array) {
        ((<warning descr="Casting 'array' to 'String[]' is redundant">String[]</warning>) array)[0] = " ";
    }

    public void bar(String[] array) {
        ((Object[]) array)[0] = new Object();
    }

    public Object obj;  
    public static void setObjValueInArray(Test t, Object val) {
      t.obj = new Object[1];
      ((Object[])t.obj)[0] = val;
    }

    public static void arrayDims() {
        int[] ints = new int[] {};
        Object foo = 1;
        ints[(Integer)  foo] = 0;
        ints[(<warning descr="Casting '1' to 'Integer' is redundant">Integer</warning>)  1] = 0;
        
        short sh = 1;
        ints[(<warning descr="Casting 'sh' to 'int' is redundant">int</warning>)sh] = 0;
    }
}