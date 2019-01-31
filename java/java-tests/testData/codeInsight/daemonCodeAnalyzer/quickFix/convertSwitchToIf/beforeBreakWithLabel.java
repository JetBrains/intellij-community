// "Replace 'switch' with 'if'" "true"
class X {
  int m(String s, int x) {
    if (x > 0) {
      SWITCH:
      swi<caret>tch (s){
      case "a":
        System.out.println("a");
        for(int i=0; i<10; i++) {
          System.out.println(i);
          if(i == x) break SWITCH;
          if(i == x*2) break;
        }
      default:
        System.out.println("d");
      }
    } else {
      return 1;
    }
    return 0;
  }
}