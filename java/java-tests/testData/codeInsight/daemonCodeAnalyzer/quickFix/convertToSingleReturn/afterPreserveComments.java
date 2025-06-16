// "Transform body to single exit-point form" "true-preview"
class Test {
    int foo(int i) {
        int result = -1;
        // comment 1
        // comment 5
        // comment 6
        if (i == 0) { // comment 2
          // comment 3
            result = 1;// comment 4
        } else if (i == 1) { // comment 7
          // comment 8
            result = 0;// comment 9
        } // comment 10
        // comment 11


        return result;
    }
}