/**
 * User: anna
 * Date: 25-Apr-2006
 */
public class Test {
  private boolean myFlag = false;
  @SuppressWarnings({"PointlessBooleanExpression"})
  boolean foo(){
    boolean flag = false;
    if (flag == false){
      flag = true;
    }
    int j = 0;
    if (myFlag) return false;
    return flag;
  }

 /*private int bar() {
    int i = 0;
    return i;
  }*/


  private void fooo(){
    this.fooo();
  }

  public void deadCode() {

  }
  public static void main(String[] args){
    Test test = new Test();
  }

    public void f() {
        class D {
            void b() {
                Runnable r = new Runnable() {
                    public void run() {
                        int i = 0;

                    }
                };
            }
        }
    }

  void ff() {
      long d = 5;
      int a = 0;
  }
}
