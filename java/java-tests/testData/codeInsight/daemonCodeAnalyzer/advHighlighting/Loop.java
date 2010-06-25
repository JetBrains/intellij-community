// continue outside of.../loop label
public class a {

  void f() {
    <error descr="Continue outside of loop">continue;</error>
    while (true) {
      continue;
    }
    do { continue; } while (true);
    switch (1) {
      case 1: <error descr="Continue outside of loop">continue;</error>
    }
    for (;;) {
      continue;
    }

    for (;;) {
      new ff() {
        void f() { 
          <error descr="Continue outside of loop">continue;</error>
        }
      };
      continue;
    }


    while (true) {
      class s {
       {
         <error descr="Continue outside of loop">continue;</error>
       }
      }
      continue;
    }

    do {
      class s {
       {
         <error descr="Continue outside of loop">continue;</error>
       }
      }
      continue;
    } while (true);



    a:
    if (2==4) {
    for (;;) {
      <error descr="Not a loop label: 'a'">continue a;</error>
    }
    }

    a:
    b:
    for (;;) {
      <error descr="Not a loop label: 'a'">continue a;</error>
    }
    


  }
}

class ff {

}
