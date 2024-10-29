// "Create 'default' branch" "false"
class X {
  void test(int i, int j) {
    switch(i) {
      case 0:
        switch (j) {
          <caret>
          default: break;
        }
    }
  }
}