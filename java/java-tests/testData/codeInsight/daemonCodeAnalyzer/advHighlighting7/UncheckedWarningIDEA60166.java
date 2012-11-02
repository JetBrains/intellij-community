class Node<E> {
  public class Details {
    public E data;
  }
  public Details addNode(Node<E> <warning descr="Parameter 'child' is never used">child</warning>) {
    return new Details();
  }
}
class Test5_1 {
  static Node<String>.Details details;
  public static void main(String[] args) {
    Node<String> stringNode = new Node<String>();
    details = <warning descr="Unchecked assignment: 'Node.Details' to 'Node<java.lang.String>.Details'">(Node.Details)stringNode.addNode(new Node<String>())</warning>;
  }
}