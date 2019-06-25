// "Extract side effects as an 'if' statement" "true"
class Z {

  void z() {
      if (foo) {
          switch (0) {
              case 0:
                  break;
              case 1:
                  break;
              default:
                  new Foo().getBar();
                  break;
          }
      }
  }
}