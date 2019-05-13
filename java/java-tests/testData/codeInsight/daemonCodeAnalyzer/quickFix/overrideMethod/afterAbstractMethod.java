// "Implement method 'foo'" "true"
abstract class Test {
  abstract void foo();
}

class TImple extends Test {
    @Override
    void foo() {
        <caret>
    }
}