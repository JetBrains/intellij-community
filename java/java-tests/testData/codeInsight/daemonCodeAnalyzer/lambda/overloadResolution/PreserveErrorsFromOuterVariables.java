
class Node {
  private <<warning descr="Type parameter 'TB' is never used">TB</warning>> void <warning descr="Private method 'addChild(java.lang.String)' is never used">addChild</warning>(String color) {
    System.out.println(color);
  }
  private <<warning descr="Type parameter 'TA' is never used">TA</warning>> void addChild(Node... nodes) {
    System.out.println(nodes);
  }

  private <TC extends Node> TC setColor() {
    return null;
  }

  {
    addChild(setColor());
  }
}