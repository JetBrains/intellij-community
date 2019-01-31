interface Inter {}

class Impl implements Inter {}

class TestGenericsInstanceof<I extends Inter>
{
    I member;

    void test() {
      boolean test = member instanceof Impl;
    }
}
