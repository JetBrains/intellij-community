import javax.swing.*;

class Test {
    public void setObj(Object obj) {
        this.obj = obj;
    }

    public void test() {
        obj = new Object();
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Object o = obj;
                if (o != null) {
                    System.out.println("x");
                }
            }
        });
        final Object u = new Object();
        if (<warning descr="Condition 'u != null' is always 'true'">u != null</warning>) {
          System.out.println("y");
        }
        obj = null;
    }

    private volatile Object obj;
}