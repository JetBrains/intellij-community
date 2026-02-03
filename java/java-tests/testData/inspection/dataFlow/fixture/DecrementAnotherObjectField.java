class Test {
  public class Node{
    private int var;
  }
  public void foo(Node n){
    assert(n.var>0);
    n.var--;
    if(n.var==0) { // condition always false
      System.out.println();
    }
  }
}