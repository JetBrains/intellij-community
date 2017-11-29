import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}