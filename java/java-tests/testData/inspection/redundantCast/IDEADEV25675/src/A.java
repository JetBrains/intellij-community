public class Test {
    public void foo(Object[] array) {
        ((String[]) array)[0] = " ";
    }

    public void bar(String[] array) {
        ((Object[]) array)[0] = new Object();
    }

    public Object obj;  
    public static void setObjValueInArray(Test t, Object val) {
      t.obj = new Object[1];
      ((Object[])t.obj)[0] = val;
    }
}