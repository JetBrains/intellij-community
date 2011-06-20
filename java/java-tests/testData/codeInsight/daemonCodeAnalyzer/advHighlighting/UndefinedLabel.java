/// labels
import java.io.*;
import java.net.*;

public class a  {

  void f(final boolean b) {
    while (b) break <error descr="Undefined label: 'lab1'">lab1</error>;
    while (b) continue <error descr="Undefined label: 'lab1'">lab1</error>;

    for (;;) {
      lab2: ;
      break <error descr="Undefined label: 'lab2'">lab2</error>;
      continue <error descr="Undefined label: 'lab2'">lab2</error>;
    }

    lab3:
    new Runnable() {
      public void run() {
        while (true) {
          if (b) break <error descr="Undefined label: 'lab3'">lab3</error>;
          if (b) continue <error descr="Undefined label: 'lab3'">lab3</error>;
        }
      }
    };
  }

  void cf() {
     boolean b = false;
     while (b) {
       lab0: break lab0;
     }

     lab1: try {
       lab2: for (;b;) if (1==3) continue lab2;
       break lab1;
     }
     finally {
       break lab1;
     }
  }
}