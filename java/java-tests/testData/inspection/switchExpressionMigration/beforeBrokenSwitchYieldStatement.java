// "Replace with 'switch' expression" "false"
import java.util.*;

class Switch {
  void test(Integer o) {
    String s = "";
    s<caret>witch (o) {
      case 1:
        s = "";
        break;
      default:
        System.out.println("1");
        s = {System.out.println("break");
        break;
        } ;
    }
  }