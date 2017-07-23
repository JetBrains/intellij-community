interface X {}

interface Y extends X {}

interface A {
  <T extends X> T get();
}

interface B extends A {
  @Override
  Y get();
}

interface C extends B {}