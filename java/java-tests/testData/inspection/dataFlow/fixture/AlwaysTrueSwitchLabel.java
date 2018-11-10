class Scratch {
  public static void main(String[] args) {
    switch("ping") {
      <warning descr="Switch label 'case \"ping\":' is the only reachable in the whole switch">case "ping":</warning>
        System.out.println("ping");
        break;
      case "pong":
        System.out.println("pong");
        break;
      case "simple":
        System.out.println("simple");
        break;
      default:
        break;
    }
    switch("ping") {
      case "pong":
        System.out.println("pong");
        break;
      <warning descr="Switch label 'case \"ping\":' is the only reachable in the whole switch">case "ping":</warning>
        System.out.println("ping");
        break;
      case "simple":
        System.out.println("simple");
        break;
      default:
        break;
    }
    switch("ping") {
      case "pong":
        System.out.println("pong");
        break;
      case "simple":
        System.out.println("simple");
        break;
      <warning descr="Switch label 'case \"ping\":' is the only reachable in the whole switch">case "ping":</warning>
        System.out.println("ping");
        break;
      default:
        break;
    }
  }
}