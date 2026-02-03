// "Create 'default' branch" "true"
class X {
  void test(int i, int j) {
    switch(i) {
      case 0:
          <caret>
        switch (j) {
          default: break;
        }
    }
  }
}