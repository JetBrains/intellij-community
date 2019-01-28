// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
    /*1*//*3*//*4*//*6*//*7*//*8*//*18*//*2*/
      switch (x +/*5*/ x) {/*14*//*9*//*11*/
          case 1 +/*10*/ 1:
              if (true /*12*/)
                  /*13*/ return 0;
              else
                  return 1;
              /*15*/
              /*16*/
          case 2:
              return 2 +/*17*/ 2;
          case 3, 4:
              System.out.println("asda");
              return 3;
          /*19*/
          default:
              return 12 /*20*/ + 12;
      }
  }
}