class Scratch {
  public static void main(String[] args) {
    switch("ping") {
      case "ping":
        System.out.println("ping");
        break;
      <warning descr="Switch label 'case \"pong\":' is unreachable">case "pong":</warning>
        System.out.println("pong");
        break;
      <warning descr="Switch label 'case \"simple\":' is unreachable">case "simple":</warning>
        System.out.println("simple");
        break;
      default:
        break;
    }
    switch("ping") {
      <warning descr="Switch label 'case \"pong\":' is unreachable">case "pong":</warning>
        System.out.println("pong");
        break;
      case "ping":
        System.out.println("ping");
        break;
      <warning descr="Switch label 'case \"simple\":' is unreachable">case "simple":</warning>
        System.out.println("simple");
        break;
      default:
        break;
    }
    switch("ping") {
      <warning descr="Switch label 'case \"pong\":' is unreachable">case "pong":</warning>
        System.out.println("pong");
        break;
      <warning descr="Switch label 'case \"simple\":' is unreachable">case "simple":</warning>
        System.out.println("simple");
        break;
      case "ping":
        System.out.println("ping");
        break;
      default:
        break;
    }
  }
}