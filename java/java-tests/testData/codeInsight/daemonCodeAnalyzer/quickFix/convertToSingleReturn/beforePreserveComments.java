// "Transform body to single exit-point form" "true-preview"
class Test {
    int fo<caret>o(int i) {
        // comment 1
        if (i == 0) { // comment 2
          // comment 3
          return 1; // comment 4
        }
        // comment 5

        // comment 6
        if (i == 1) { // comment 7
          // comment 8
          return 0; // comment 9
        }

        // comment 10
        return -1; // comment 11
    }
}