// break outside of
public class a {

  void f() {
    <error descr="Break outside switch or loop">break;</error>
    while (true) {
      break;
    }
    do { break; } while (true);
    switch (1) {
      case 1: break;
    }
    for (;;) {
      break;
    }

    for (;;) {
      new ff() {
        void f() { 
          <error descr="Break outside switch or loop">break;</error>
        }
      };
      break;
    }


    while (true) {
      class s {
       {
         <error descr="Break outside switch or loop">break;</error>
       }
      }
      break;
    }

    do {
      class s {
       {
         <error descr="Break outside switch or loop">break;</error>
       }
      }
      break;
    } while (true);

  }
}

class ff {
 
}
