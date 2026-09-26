class Scratch {
  public static void main(String[] args) {
    switch("ping") {
      case <warning descr="Switch label '\"ping\"' is the only reachable in the whole switch">"ping"</warning>:
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
      case <warning descr="Switch label '\"ping\"' is the only reachable in the whole switch">"ping"</warning>:
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
      case <warning descr="Switch label '\"ping\"' is the only reachable in the whole switch">"ping"</warning>:
        System.out.println("ping");
        break;
      default:
        break;
    }
  }
}