package com.siyeh.ipp;

public class BasTestCase {

    int test() {
      return (new int[][]{{1, 2}, {3, 4}}[1][1]);
    }

    boolean test2(int i) {
      switch (i ) {
        case 3: return true;
      }
      return true;
    }

    boolean test3()
    {
        if (.equals("")) {
           return true;
         }
         return false;

    }

    void teset4()
    {
          int i = 0;
          i = i + ;
    }

    boolean test5(int i) {
      if (i ==) {
        return false;
      }
      return true;
    }

    boolean test6() {
        if () {
          return false;
        } else {
          return true;
        }
      }

}
