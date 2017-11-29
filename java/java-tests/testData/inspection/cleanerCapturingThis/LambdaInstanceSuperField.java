import java.lang.ref.Cleaner;

class Base {
  int fileDescriptor;
}

class CleanerCapturingThis extends Base {
  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}